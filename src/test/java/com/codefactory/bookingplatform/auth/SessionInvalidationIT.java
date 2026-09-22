package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-021 - acceptance criterion "cierre de sesión invalida la sesión".
 *
 * <p>{@code AuthFlowIT.logoutRevokesSession} only checks that {@code signOut} is <em>invoked</em>
 * with the bearer value. That is the call, not the effect. These tests log in for real, use the
 * token the provider handed out, log out, and then try the same token again.
 */
class SessionInvalidationIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";
    private static final String PASSWORD = "Str0ng!Pass";

    @Test
    @DisplayName("HU-021 AC 'cierre de sesión invalida la sesión': logout revokes the session at the provider")
    void logoutRevokesTheIssuedSession() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertTrue(identityProvider.isSessionRevoked(accessToken),
                "logout must revoke at the provider the exact token the client was holding");
    }

    /**
     * DEFECT D8 (characterization test).
     *
     * <p>The criterion says the session must stop working after logout. It does not: the access
     * token is a self-contained JWT validated offline against the JWKS, and nothing in the request
     * path asks the provider whether that session is still alive, nor is there a local deny list.
     * Revoking at Supabase only kills the refresh token, so the access token keeps opening every
     * protected endpoint until its own {@code exp} (900 s per {@code LoginResponse.expiresIn}).
     *
     * <p>ADR-0003 accepts this as a trade-off ("un access token robado sigue siendo válido hasta su
     * expiración corta"), but the HU-021 criterion is written in absolute terms, so as far as
     * traceability goes the criterion is only partially met: revocation reaches the provider, the
     * session does not actually close.
     *
     * <p>This test pins the behaviour that exists today. It is deliberately written to fail the
     * moment someone closes the gap - at which point the expectation below becomes 401 and the
     * criterion is finally met.
     */
    @Test
    @DisplayName("HU-021 DEFECT D8: after logout the access token is still accepted (no revocation check)")
    void accessTokenSurvivesLogout() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        int statusAfterLogout = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();

        assertEquals(200, statusAfterLogout,
                "Current behaviour pinned: HU-021 asks for 401 here. If this now returns 401 the defect "
                        + "was fixed - flip the expectation and move this test out of the DEFECT group.");
    }

    @Test
    @DisplayName("HU-021 AC: logging out twice is idempotent and never leaks an upstream error")
    void logoutIsIdempotent() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-021 AC: logout without a bearer token is rejected with 401")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
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
