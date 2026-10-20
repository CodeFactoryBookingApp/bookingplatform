package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-021 - acceptance criterion "cierre de sesión invalida la sesión".
 *
 * <p>These tests log in for real and log out with the token the provider handed out. The revocation
 * at the provider is asserted by {@code HappyPathAcceptanceIT.fullHappyPath}. That the access token
 * keeps opening protected endpoints after logout is a known defect (BUG-006) and is deliberately
 * not pinned here as if it were the expected behaviour.
 *
 * <p>Arrange-Act-Assert: the real login (and, where the scenario needs it, an earlier logout) is the
 * Arrange, guarded with the status each step must produce; the request under test is the Act.
 */
class SessionInvalidationIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";
    private static final String PASSWORD = "Str0ng!Pass";

    @Test
    @DisplayName("HU-021 AC: logging out twice is idempotent and never leaks an upstream error")
    void logoutIsIdempotent() throws Exception {
        // Arrange
        String accessToken = loginAndGetAccessToken();
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Act
        ResultActions secondLogout = mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken));

        // Assert
        secondLogout.andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-021 AC: logout without a bearer token is rejected with 401")
    void logoutRequiresAuthentication() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/logout"));

        // Assert
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    private String loginAndGetAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"" + identityProvider.currentEmailToken(EMAIL) + "\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    }
}
