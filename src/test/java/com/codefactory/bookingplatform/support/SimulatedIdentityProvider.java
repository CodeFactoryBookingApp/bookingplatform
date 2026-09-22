package com.codefactory.bookingplatform.support;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * In-memory stand-in for Supabase Auth (GoTrue), installed on top of the {@code @MockitoBean}
 * of {@link IdentityProviderPort} that the integration tests already use.
 *
 * <p>The existing ITs stub the port call by call, which is enough to check one branch but cannot
 * express behaviour that depends on <em>history</em>. The acceptance criteria "recuperación de
 * contraseña con enlace de un solo uso" (HU-021) and "reenvío del correo de verificación con
 * enlace vigente" (HU-001) are exactly that: whether a token is accepted depends on whether it was
 * already redeemed. So this fake keeps the state a real provider keeps:
 *
 * <ul>
 *   <li>users, with their password and whether the email is confirmed;</li>
 *   <li>one-time email-verification tokens, burned on redemption;</li>
 *   <li>one-time password-recovery tokens, burned on redemption;</li>
 *   <li>issued access tokens and which of them have been revoked by {@code signOut}.</li>
 * </ul>
 *
 * <p>Access tokens are real RS256 JWTs signed with the key published by {@link LocalJwksServer},
 * so a token handed out by {@code /login} is a token the production {@code JwtDecoder} accepts.
 */
public final class SimulatedIdentityProvider {

    /** Prefix of the {@code token_hash} that travels in the verification email link. */
    public static final String EMAIL_TOKEN_PREFIX = "email-otp-";
    /** Prefix of the {@code token_hash} that travels in the recovery email link. */
    public static final String RECOVERY_TOKEN_PREFIX = "recovery-otp-";

    private final TestJwks jwks;

    private final Map<String, UUID> userIdsByEmail = new ConcurrentHashMap<>();
    private final Map<String, String> passwordsByEmail = new ConcurrentHashMap<>();
    private final Map<String, AppRole> rolesByEmail = new ConcurrentHashMap<>();
    private final Set<String> confirmedEmails = ConcurrentHashMap.newKeySet();

    private final Map<String, String> liveEmailTokens = new ConcurrentHashMap<>();
    private final Map<String, String> liveRecoveryTokens = new ConcurrentHashMap<>();
    private final Set<String> redeemedTokens = ConcurrentHashMap.newKeySet();

    private final Map<String, String> emailByAccessToken = new ConcurrentHashMap<>();
    private final Set<String> revokedAccessTokens = ConcurrentHashMap.newKeySet();

    private int emailTokenSequence;
    private int recoveryTokenSequence;

    public SimulatedIdentityProvider(TestJwks jwks) {
        this.jwks = jwks;
    }

    /** Wires every port method to this fake. Call it from {@code @BeforeEach}. */
    public void install(IdentityProviderPort mock) {
        doAnswer(invocation -> createUser(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))
                .when(mock).createUser(anyString(), anyString(), any(AppRole.class));

        doAnswer(invocation -> {
            deleteUser(invocation.getArgument(0));
            return null;
        }).when(mock).deleteUser(any(UUID.class));

        doAnswer(invocation -> requestPasswordToken(invocation.getArgument(0), invocation.getArgument(1)))
                .when(mock).requestPasswordToken(anyString(), anyString());

        doAnswer(invocation -> verifyEmailToken(invocation.getArgument(0)))
                .when(mock).verifyEmailToken(anyString());

        doAnswer(invocation -> {
            resendSignupVerification(invocation.getArgument(0));
            return null;
        }).when(mock).resendSignupVerification(anyString());

        doAnswer(invocation -> {
            sendPasswordRecovery(invocation.getArgument(0));
            return null;
        }).when(mock).sendPasswordRecovery(anyString());

        doAnswer(invocation -> {
            resetPasswordWithToken(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mock).resetPasswordWithToken(anyString(), anyString());

        doAnswer(invocation -> {
            signOut(invocation.getArgument(0));
            return null;
        }).when(mock).signOut(anyString());
    }

    // --- provider behaviour -------------------------------------------------

    private UUID createUser(String email, String password, AppRole role) {
        String key = normalize(email);
        if (userIdsByEmail.containsKey(key)) {
            throw new UpstreamAuthException(UpstreamAuthError.USER_ALREADY_EXISTS, "User already registered");
        }
        UUID userId = UUID.randomUUID();
        userIdsByEmail.put(key, userId);
        passwordsByEmail.put(key, password);
        rolesByEmail.put(key, role);
        mintEmailToken(key);
        return userId;
    }

    private void deleteUser(UUID userId) {
        userIdsByEmail.entrySet().removeIf(entry -> entry.getValue().equals(userId));
    }

    private AuthTokens requestPasswordToken(String email, String password) {
        String key = normalize(email);
        UUID userId = userIdsByEmail.get(key);
        if (userId == null || !passwordsByEmail.get(key).equals(password)) {
            throw new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "Invalid login credentials");
        }
        if (!confirmedEmails.contains(key)) {
            throw new UpstreamAuthException(UpstreamAuthError.EMAIL_NOT_CONFIRMED, "Email not confirmed");
        }
        String accessToken = jwks.accessToken(userId, key, rolesByEmail.get(key).name());
        emailByAccessToken.put(accessToken, key);
        return new AuthTokens(accessToken, "refresh-" + UUID.randomUUID(), "bearer", 900);
    }

    private ConfirmedUser verifyEmailToken(String tokenHash) {
        String email = liveEmailTokens.get(tokenHash);
        if (email == null) {
            // Already redeemed tokens land here too: a one-time link is single use.
            throw new UpstreamAuthException(UpstreamAuthError.TOKEN_INVALID,
                    redeemedTokens.contains(tokenHash)
                            ? "Email link is invalid or has already been used"
                            : "Email link is invalid or has expired");
        }
        liveEmailTokens.remove(tokenHash);
        redeemedTokens.add(tokenHash);
        confirmedEmails.add(email);
        return new ConfirmedUser(userIdsByEmail.get(email), email);
    }

    private void resendSignupVerification(String email) {
        String key = normalize(email);
        if (!userIdsByEmail.containsKey(key)) {
            throw new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "User not found");
        }
        mintEmailToken(key);
    }

    private void sendPasswordRecovery(String email) {
        String key = normalize(email);
        if (!userIdsByEmail.containsKey(key)) {
            throw new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "User not found");
        }
        liveRecoveryTokens.values().removeIf(key::equals);
        String token = RECOVERY_TOKEN_PREFIX + (++recoveryTokenSequence);
        liveRecoveryTokens.put(token, key);
    }

    private void resetPasswordWithToken(String tokenHash, String newPassword) {
        String email = liveRecoveryTokens.get(tokenHash);
        if (email == null) {
            throw new UpstreamAuthException(UpstreamAuthError.TOKEN_INVALID,
                    redeemedTokens.contains(tokenHash)
                            ? "Recovery link is invalid or has already been used"
                            : "Recovery link is invalid or has expired");
        }
        liveRecoveryTokens.remove(tokenHash);
        redeemedTokens.add(tokenHash);
        passwordsByEmail.put(email, newPassword);
        // A real provider drops every active session when the password changes.
        emailByAccessToken.forEach((token, owner) -> {
            if (owner.equals(email)) {
                revokedAccessTokens.add(token);
            }
        });
    }

    private void signOut(String accessToken) {
        revokedAccessTokens.add(accessToken);
    }

    private void mintEmailToken(String email) {
        liveEmailTokens.values().removeIf(email::equals);
        String token = EMAIL_TOKEN_PREFIX + (++emailTokenSequence);
        liveEmailTokens.put(token, email);
    }

    // --- test-side inspection ----------------------------------------------

    /** The {@code token_hash} currently travelling in the verification link for that email. */
    public String currentEmailToken(String email) {
        return findToken(liveEmailTokens, email)
                .orElseThrow(() -> new IllegalStateException("No live verification token for " + email));
    }

    /** The {@code token_hash} currently travelling in the recovery link for that email. */
    public String currentRecoveryToken(String email) {
        return findToken(liveRecoveryTokens, email)
                .orElseThrow(() -> new IllegalStateException("No live recovery token for " + email));
    }

    public boolean isTokenRedeemed(String tokenHash) {
        return redeemedTokens.contains(tokenHash);
    }

    public boolean isSessionRevoked(String accessToken) {
        return revokedAccessTokens.contains(accessToken);
    }

    public boolean isEmailConfirmed(String email) {
        return confirmedEmails.contains(normalize(email));
    }

    public UUID userIdOf(String email) {
        return userIdsByEmail.get(normalize(email));
    }

    private static Optional<String> findToken(Map<String, String> tokens, String email) {
        String key = normalize(email);
        return tokens.entrySet().stream()
                .filter(entry -> entry.getValue().equals(key))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static String normalize(String email) {
        return email.toLowerCase(java.util.Locale.ROOT);
    }
}
