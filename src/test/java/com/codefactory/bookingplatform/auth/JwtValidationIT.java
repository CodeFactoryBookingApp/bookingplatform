package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-021 - "inicio de sesión exitoso según rol", exercised through the real token pipeline.
 *
 * <p>Every other test in the suite authenticates with
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}, which injects an already-built
 * {@code JwtAuthenticationToken} and therefore never touches {@code SecurityConfig.jwtDecoder()}.
 * A wrong JWKS URI, a missing algorithm or a missing issuer check would sail through the whole
 * suite and only show up in production. These tests send a raw {@code Authorization: Bearer}
 * header so the request goes through {@code BearerTokenAuthenticationFilter} ->
 * {@code NimbusJwtDecoder} -> JWKS fetch -> signature, {@code exp} and {@code iss} validation.
 *
 * @see com.codefactory.bookingplatform.shared.config.SecurityConfig#jwtDecoder()
 */
class JwtValidationIT extends JwtIntegrationTestBase {

    private static final UUID USER_ID = UUID.fromString("9f1c2f3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f");
    private static final String EMAIL = "ana.perez@example.com";

    @Test
    @DisplayName("HU-021 AC: a well formed token is accepted by the production JwtDecoder and carries the role")
    void wellFormedTokenIsAccepted() throws Exception {
        String token = jwks().accessToken(USER_ID, EMAIL, "CLIENT");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                // app_metadata.role wins; the ADMIN planted in user_metadata must be ignored
                .andExpect(jsonPath("$.role").value("CLIENT"));

        assertTrue(JWKS_SERVER.requestCount() > 0,
                "the decoder must have fetched the JWK set over HTTP; if this is 0 the decoder was bypassed");
    }

    @Test
    @DisplayName("HU-021 AC: a token minted by another issuer is rejected with 401 (JwtIssuerValidator)")
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String token = jwks().tokenFromAnotherIssuer(USER_ID, EMAIL, "CLIENT");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("HU-021 AC: an expired token is rejected with 401 (JwtTimestampValidator)")
    void expiredTokenIsRejected() throws Exception {
        String token = jwks().expiredAccessToken(USER_ID, EMAIL, "CLIENT");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("HU-021 AC: a token signed with a key outside the JWK set is rejected with 401")
    void forgedSignatureIsRejected() throws Exception {
        String token = jwks().tokenWithForgedSignature(USER_ID, EMAIL, "CLIENT");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("HU-021 AC: a malformed bearer value is rejected with 401 and never reaches the controller")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("HU-021 AC: a valid token without app_metadata.role authenticates but carries no role")
    void tokenWithoutRoleAuthenticatesWithoutAuthority() throws Exception {
        String token = jwks().accessToken(USER_ID, EMAIL, "");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").isEmpty());
    }
}
