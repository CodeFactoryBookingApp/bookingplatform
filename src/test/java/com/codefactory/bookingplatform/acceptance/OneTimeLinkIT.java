package com.codefactory.bookingplatform.acceptance;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-001 / HU-021 - acceptance criterion "enlace de un solo uso".
 *
 * <p>The existing ITs cover the <em>expired</em> link. What gives the criterion its name, though,
 * is re-use: a link that already did its job must not do it a second time. These tests redeem a
 * real {@code token_hash} and then replay it, both for the email-verification link (HU-001) and for
 * the password-recovery link (HU-021), and check that nothing changes on the replay.
 *
 * <p>Arrange-Act-Assert: the redemptions that set the scene are the Arrange (guarded with the
 * status they must produce, and with the provider-side precondition where the scenario depends on
 * it); the single request under test is the Act.
 */
class OneTimeLinkIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";
    private static final String NEW_PASSWORD = "N3w!StrongPass";
    private static final String REPLAYED_PASSWORD = "An0ther!Pass";

    @Test
    @DisplayName("HU-001 AC 'reenvío del correo de verificación con enlace vigente': the email link is single use")
    void emailVerificationLinkIsRejectedOnSecondUse() throws Exception {
        // Arrange
        UUID clientId = register();
        String tokenHash = identityProvider.currentEmailToken(EMAIL);
        // Given the client clicks the link once
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(tokenHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        // precondition: the first redemption did its job and burned the token
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertTrue(identityProvider.isTokenRedeemed(tokenHash), "the provider must have burned the token");

        // Act
        // When the very same token_hash is replayed (forwarded mail, browser history, attacker)
        ResultActions replay = mockMvc.perform(post("/api/v1/registrations/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload(tokenHash)));

        // Assert
        replay.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        // Then the client keeps the state the first redemption left, no second transition
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
    }

    @Test
    @DisplayName("HU-001 AC: resending the verification email answers 202 and mints a new link")
    void resendingVerificationMintsANewLink() throws Exception {
        // Arrange
        register();
        String firstToken = identityProvider.currentEmailToken(EMAIL);

        // Act
        ResultActions resend = mockMvc.perform(post("/api/v1/registrations/verification-resends")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailPayload()));

        // Assert
        resend.andExpect(status().isAccepted());
        assertNotEquals(firstToken, identityProvider.currentEmailToken(EMAIL), "a resend must mint a new link");
    }

    @Test
    @DisplayName("HU-001 AC: after a resend the previous verification link is rejected and the client stays pending")
    void resendingVerificationInvalidatesThePreviousLink() throws Exception {
        // Arrange
        UUID clientId = register();
        String firstToken = identityProvider.currentEmailToken(EMAIL);
        resendVerification();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/registrations/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload(firstToken)));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
        assertEquals(ClientStatus.PENDING_VERIFICATION, storedClient(clientId).getStatus());
    }

    @Test
    @DisplayName("HU-021 AC 'recuperación de contraseña con enlace de un solo uso': the recovery link is single use")
    void passwordRecoveryLinkIsRejectedOnSecondUse() throws Exception {
        // Arrange
        registerAndConfirm();
        String tokenHash = requestRecoveryLink();
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        // precondition: the first redemption burned the token
        assertTrue(identityProvider.isTokenRedeemed(tokenHash), "the provider must have burned the recovery token");

        // Act
        // Replaying the same link must not let anyone set the password again
        ResultActions replay = mockMvc.perform(post("/api/v1/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPayload(tokenHash, REPLAYED_PASSWORD)));

        // Assert
        replay.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("HU-021 AC: the password sent with a replayed recovery link does not open the account")
    void passwordFromAReplayedRecoveryLinkIsNotSet() throws Exception {
        // Arrange
        registerAndConfirm();
        redeemRecoveryLinkThenReplayIt();

        // Act
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(REPLAYED_PASSWORD)));

        // Assert
        login.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("HU-021 AC: after a replay, the password that counts is the one set by the single valid redemption")
    void passwordFromTheSingleValidRedemptionStillWorks() throws Exception {
        // Arrange
        registerAndConfirm();
        redeemRecoveryLinkThenReplayIt();

        // Act
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(NEW_PASSWORD)));

        // Assert
        login.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("HU-021 AC: a recovery link rejected by the password policy is NOT consumed")
    void weakPasswordDoesNotBurnTheRecoveryLink() throws Exception {
        // Arrange
        registerAndConfirm();
        String tokenHash = requestRecoveryLink();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPayload(tokenHash, "todolowercase")));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_WEAK"));
        assertFalse(identityProvider.isTokenRedeemed(tokenHash),
                "a request stopped by the local policy must leave the link usable");
    }

    @Test
    @DisplayName("HU-021 AC: after a weak password was rejected, the same recovery link still resets the password")
    void recoveryLinkStillWorksAfterAWeakPasswordWasRejected() throws Exception {
        // Arrange
        registerAndConfirm();
        String tokenHash = requestRecoveryLink();
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, "todolowercase")))
                .andExpect(status().isBadRequest());

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPayload(tokenHash, NEW_PASSWORD)));

        // Assert
        result.andExpect(status().isNoContent());
    }

    // --- helpers ------------------------------------------------------------

    private UUID register() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        UUID clientId = identityProvider.userIdOf(EMAIL);
        assertNotNull(clientId, "the provider must have provisioned the auth user");
        return clientId;
    }

    private void registerAndConfirm() throws Exception {
        register();
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(identityProvider.currentEmailToken(EMAIL))))
                .andExpect(status().isOk());
    }

    private void resendVerification() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/verification-resends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailPayload()))
                .andExpect(status().isAccepted());
    }

    /** Asks for a recovery email and returns the token_hash of the link it carries. */
    private String requestRecoveryLink() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailPayload()))
                .andExpect(status().isAccepted());
        return identityProvider.currentRecoveryToken(EMAIL);
    }

    /** Redeems a recovery link with {@link #NEW_PASSWORD}, then replays it with {@link #REPLAYED_PASSWORD}. */
    private void redeemRecoveryLinkThenReplayIt() throws Exception {
        String tokenHash = requestRecoveryLink();
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, REPLAYED_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    private ClientEntity storedClient(UUID clientId) {
        return clientJpaRepository.findById(clientId).orElseThrow();
    }

    private static String emailPayload() {
        return "{\"email\":\"" + EMAIL + "\"}";
    }

    private static String confirmPayload(String tokenHash) {
        return "{\"tokenHash\":\"" + tokenHash + "\"}";
    }

    private static String resetPayload(String tokenHash, String newPassword) {
        return "{\"tokenHash\":\"" + tokenHash + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private static String loginPayload(String password) {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
    }
}
