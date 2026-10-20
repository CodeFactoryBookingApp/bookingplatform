package com.codefactory.bookingplatform.acceptance;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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
 *
 * <p>Each step is its own test: the steps that come before it are the Arrange (driven through the
 * same HTTP API and guarded with their expected status, so a broken earlier step fails fast), the
 * step itself is the Act, and its effects are the Assert. {@link #fullHappyPath()} walks the whole
 * path and acts on the last step, logout.
 */
class HappyPathAcceptanceIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";
    private static final String PASSWORD = "Str0ng!Pass";

    @Test
    @DisplayName("HU-001 step 1: registration answers 201 and stores the client as PENDING_VERIFICATION under the provider's user id")
    void registrationStoresAPendingClient() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationPayload(EMAIL, DOCUMENT)));

        // Assert
        String registrationBody = result
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
    }

    @Test
    @DisplayName("HU-001 + HU-021 step 2: logging in before confirming the email answers 403, the account is not usable yet")
    void loginBeforeConfirmingTheEmailIsForbidden() throws Exception {
        // Arrange
        UUID clientId = register();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload()));

        // Assert
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_CONFIRMED"));
        assertEquals(ClientStatus.PENDING_VERIFICATION, storedClient(clientId).getStatus());
    }

    @Test
    @DisplayName("HU-001 step 3: confirming the email with the one-time token_hash answers 200 and flips the row to ACTIVE")
    void emailConfirmationActivatesTheClient() throws Exception {
        // Arrange
        UUID clientId = register();
        String tokenHash = identityProvider.currentEmailToken(EMAIL);

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/registrations/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload(tokenHash)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertTrue(identityProvider.isEmailConfirmed(EMAIL));
    }

    @Test
    @DisplayName("HU-021 step 4: logging in after confirming answers 200 with a real access token")
    void loginAfterConfirmingIssuesAnAccessToken() throws Exception {
        // Arrange
        registerAndConfirm();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload()));

        // Assert
        String loginBody = result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        assertNotNull(accessToken);
    }

    @Test
    @DisplayName("HU-021 step 5: /me with the issued token answers 200 through the production JwtDecoder")
    void meWithTheIssuedTokenReturnsTheClient() throws Exception {
        // Arrange
        UUID clientId = registerAndConfirm();
        String accessToken = login();

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("HU-001 + HU-021: register, confirm the email, log in, read /me and log out")
    void fullHappyPath() throws Exception {
        // Arrange
        UUID clientId = registerAndConfirm();
        String accessToken = login();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken));

        // Assert
        // Logout: 204 and the session is revoked upstream
        result.andExpect(status().isNoContent());
        assertTrue(identityProvider.isSessionRevoked(accessToken));
        // The client row is untouched by the session lifecycle
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertEquals(1, clientJpaRepository.count());
    }

    @Test
    @DisplayName("HU-021: after a successful login the failed-attempt counter does not lock the account")
    void successfulLoginDoesNotAccumulateLockState() throws Exception {
        // Arrange
        registerAndConfirm();
        for (int attempt = 1; attempt <= 5; attempt++) {
            login();
        }

        // Act
        ResultActions sixthLogin = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload()));

        // Assert
        sixthLogin.andExpect(status().isOk());
    }

    // --- helpers: earlier steps of the path, each guarded with its expected status -------------

    private UUID register() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated());
        return identityProvider.userIdOf(EMAIL);
    }

    private UUID registerAndConfirm() throws Exception {
        UUID clientId = register();
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(identityProvider.currentEmailToken(EMAIL))))
                .andExpect(status().isOk());
        return clientId;
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ClientEntity storedClient(UUID clientId) {
        return clientJpaRepository.findById(clientId).orElseThrow();
    }

    private static String confirmPayload(String tokenHash) {
        return "{\"tokenHash\":\"" + tokenHash + "\"}";
    }

    private static String loginPayload() {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
