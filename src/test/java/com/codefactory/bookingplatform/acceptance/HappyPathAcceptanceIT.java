package com.codefactory.bookingplatform.acceptance;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-001 + HU-021 end to end, over HTTP and against PostgreSQL: the exact path shown in the demo.
 *
 * <p>Registration -&gt; email confirmation -&gt; login -&gt; {@code /me} with the issued token -&gt;
 * logout. Every step asserts the HTTP status and the state of the client row, so if any of the two
 * stories regresses this is the test that says where.
 */
class HappyPathAcceptanceIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";
    private static final String PASSWORD = "Str0ng!Pass";

    @Test
    @DisplayName("HU-001 + HU-021: register, confirm the email, log in, read /me and log out")
    void fullHappyPath() throws Exception {
        // 1. Registration: 201 and the client lands in the database as PENDING_VERIFICATION
        String registrationBody = mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse().getContentAsString();

        UUID clientId = UUID.fromString(JsonPath.read(registrationBody, "$.clientId"));
        assertEquals(identityProvider.userIdOf(EMAIL), clientId,
                "the client id must be the auth user id assigned by the provider");
        ClientEntity stored = storedClient(clientId);
        assertEquals(ClientStatus.PENDING_VERIFICATION, stored.getStatus());
        assertEquals(DOCUMENT, stored.getDocument());
        assertFalse(identityProvider.isEmailConfirmed(EMAIL));

        // 2. Login before confirming: 403, the account is not usable yet
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_CONFIRMED"));
        assertEquals(ClientStatus.PENDING_VERIFICATION, storedClient(clientId).getStatus());

        // 3. Email confirmation with the one-time token_hash: 200 and the row flips to ACTIVE
        String tokenHash = identityProvider.currentEmailToken(EMAIL);
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"" + tokenHash + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertTrue(identityProvider.isEmailConfirmed(EMAIL));

        // 4. Login: 200 with a real access token
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        assertNotNull(accessToken);

        // 5. /me with that token: 200, and it goes through the production JwtDecoder
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("CLIENT"));

        // 6. Logout: 204 and the session is revoked upstream
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        assertTrue(identityProvider.isSessionRevoked(accessToken));

        // The client row is untouched by the session lifecycle
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertEquals(1, clientJpaRepository.count());
    }

    @Test
    @DisplayName("HU-021: after a successful login the failed-attempt counter does not lock the account")
    void successfulLoginDoesNotAccumulateLockState() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"" + identityProvider.currentEmailToken(EMAIL) + "\"}"))
                .andExpect(status().isOk());

        for (int attempt = 1; attempt <= 6; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload()))
                    .andExpect(status().isOk());
        }
    }

    private ClientEntity storedClient(UUID clientId) {
        return clientJpaRepository.findById(clientId).orElseThrow();
    }

    private static String loginPayload() {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
