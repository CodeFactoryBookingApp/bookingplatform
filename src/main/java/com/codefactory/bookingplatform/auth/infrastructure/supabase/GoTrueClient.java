package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for Supabase Auth (GoTrue). The secret key never leaves the backend.
 * All endpoints are relative to {SUPABASE_URL}/auth/v1.
 */
@Component
public class GoTrueClient implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(GoTrueClient.class);

    /** Error-mapping context of the /verify endpoint, shared by email confirmation and password reset. */
    private static final String VERIFY_CONTEXT = "verify";

    private final RestClient restClient;
    private final SupabaseProperties properties;

    public GoTrueClient(SupabaseProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.url() + "/auth/v1")
                .build();
    }

    @Override
    public UUID createUser(String email, String password, AppRole role) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("email_confirm", false);
        body.put("app_metadata", Map.of("role", role.name()));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/admin/users")
                    .headers(this::adminHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return requireUuid(response, "id");
        } catch (RestClientResponseException ex) {
            throw mapError(ex, "createUser");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public void deleteUser(UUID userId) {
        try {
            restClient.delete()
                    .uri("/admin/users/{id}", userId)
                    .headers(this::adminHeaders)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapError(ex, "deleteUser");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public AuthTokens requestPasswordToken(String email, String password) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/token").queryParam("grant_type", "password").build())
                    .headers(this::apiHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(Map.class);
            return new AuthTokens(
                    String.valueOf(requireField(response, "access_token")),
                    String.valueOf(requireField(response, "refresh_token")),
                    String.valueOf(response.getOrDefault("token_type", "bearer")),
                    requireExpiresIn(response));
        } catch (RestClientResponseException ex) {
            throw mapError(ex, "token");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public ConfirmedUser verifyEmailToken(String tokenHash) {
        Map<String, Object> response = verify(tokenHash, "email", null);
        // GoTrue answers OTP verification with a session carrying the user nested
        // ({"access_token": ..., "user": {...}}), not with the bare user object.
        Object nestedUser = response.get("user");
        Map<String, Object> user;
        if (nestedUser instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) nested;
            user = casted;
        } else {
            user = response;
        }
        // El correo no lo consume nadie: ConfirmEmailUseCase resuelve el cliente por
        // userId. Exigirlo con requireField convertiria una respuesta sin ese campo en
        // un 502 para un valor que se descarta, asi que se deja tolerante a proposito.
        return new ConfirmedUser(
                requireUuid(user, "id"),
                user.get("email") == null ? null : String.valueOf(user.get("email")));
    }

    @Override
    public void resendSignupVerification(String email) {
        try {
            restClient.post()
                    .uri("/resend")
                    .headers(this::apiHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "type", "signup"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapError(ex, "resend");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public void sendPasswordRecovery(String email) {
        try {
            restClient.post()
                    .uri("/recover")
                    .headers(this::apiHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapError(ex, "recover");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public void resetPasswordWithToken(String tokenHash, String newPassword) {
        verify(tokenHash, "recovery", newPassword);
    }

    @Override
    public void signOut(String accessToken) {
        try {
            restClient.post()
                    .uri("/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("apikey", properties.secretKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                throw new UpstreamAuthException(UpstreamAuthError.TOKEN_INVALID,
                        "Session is no longer valid: " + ex.getMessage());
            }
            throw mapError(ex, "logout");
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private Map<String, Object> verify(String tokenHash, String type, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("token_hash", tokenHash);
        body.put("type", type);
        if (password != null) {
            body.put("password", password);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/verify")
                    .headers(this::apiHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response != null ? response : Map.of();
        } catch (RestClientResponseException ex) {
            throw mapError(ex, VERIFY_CONTEXT);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private void adminHeaders(HttpHeaders headers) {
        headers.set("apikey", properties.secretKey());
        headers.setBearerAuth(properties.secretKey());
    }

    private void apiHeaders(HttpHeaders headers) {
        headers.set("apikey", properties.secretKey());
    }

    private Object requireField(Map<String, Object> response, String field) {
        if (response == null || response.get(field) == null) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, missing field: " + field);
        }
        return response.get(field);
    }

    /**
     * A malformed id is an unusable provider answer, exactly like a missing one:
     * the raw {@link IllegalArgumentException} from {@code UUID.fromString} would
     * escape the adapter and surface as a 500 instead of an upstream error.
     */
    private UUID requireUuid(Map<String, Object> response, String field) {
        String raw = String.valueOf(requireField(response, field));
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, field is not a valid UUID: " + field, ex);
        }
    }

    /**
     * {@code expires_in} is optional (defaults to one hour), but a present value that
     * is not a whole number — {@code "never"}, or the perfectly valid JSON number
     * {@code 3600.0} — must not escape as a raw {@link NumberFormatException}.
     */
    private long requireExpiresIn(Map<String, Object> response) {
        try {
            return Long.parseLong(String.valueOf(response.getOrDefault("expires_in", "3600")));
        } catch (NumberFormatException ex) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, field is not a number: expires_in", ex);
        }
    }

    private UpstreamAuthException unavailable(Exception cause) {
        log.error("Identity provider unreachable: {}", cause.getMessage());
        return new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                "Identity provider is unreachable", cause);
    }

    private UpstreamAuthException mapError(RestClientResponseException ex, String context) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString().toLowerCase(Locale.ROOT);
        log.warn("GoTrue {} failed with status {}: {}", context, status, ex.getResponseBodyAsString());

        if (status == 429) {
            return new UpstreamAuthException(UpstreamAuthError.RATE_LIMITED, "Identity provider rate limit reached");
        }
        if (body.contains("email not confirmed") || body.contains("email_not_confirmed")) {
            return new UpstreamAuthException(UpstreamAuthError.EMAIL_NOT_CONFIRMED, "Email not confirmed");
        }
        if (body.contains("already exists") || body.contains("user_exists") || body.contains("email_exists")) {
            return new UpstreamAuthException(UpstreamAuthError.USER_ALREADY_EXISTS, "User already exists");
        }
        // GoTrue answers 404 to an expired or already consumed OTP, so "expired" has to
        // be checked before the 404 rule: otherwise a stale verification link is reported
        // as USER_NOT_FOUND and the user reads "usuario no encontrado".
        if (body.contains("expired")) {
            return new UpstreamAuthException(UpstreamAuthError.TOKEN_EXPIRED, "Token has expired");
        }
        // A bare 404 from /verify is about the one-time token, never about a user; it is
        // classified below by the verify branch. Every other endpoint keeps the old rule.
        if (body.contains("not found") || (status == 404 && !VERIFY_CONTEXT.equals(context))) {
            return new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "User not found");
        }
        switch (context) {
            case "token":
                if (status == 400 || status == 401) {
                    return new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "Invalid login credentials");
                }
                break;
            case VERIFY_CONTEXT:
                if (status == 400 || status == 403 || status == 404) {
                    return new UpstreamAuthException(UpstreamAuthError.TOKEN_INVALID, "Invalid or already used token");
                }
                break;
            default:
                break;
        }
        return new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                "Identity provider error in " + context + ": HTTP " + status);
    }
}
