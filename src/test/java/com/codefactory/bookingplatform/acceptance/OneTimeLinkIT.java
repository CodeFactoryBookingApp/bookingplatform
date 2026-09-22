package com.codefactory.bookingplatform.acceptance;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
 */
class OneTimeLinkIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";

    @Test
    @DisplayName("HU-001 AC 'reenvío del correo de verificación con enlace vigente': the email link is single use")
    void emailVerificationLinkIsRejectedOnSecondUse() throws Exception {
        UUID clientId = register();
        String tokenHash = identityProvider.currentEmailToken(EMAIL);

        // Given the client clicks the link once
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(tokenHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
        assertTrue(identityProvider.isTokenRedeemed(tokenHash), "the provider must have burned the token");

        // When the very same token_hash is replayed (forwarded mail, browser history, attacker)
        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(tokenHash)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());

        // Then the client keeps the state the first redemption left, no second transition
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
    }

    @Test
    @DisplayName("HU-001 AC: resending the verification email invalidates the previous link and issues a live one")
    void resendingVerificationSupersedesThePreviousLink() throws Exception {
        UUID clientId = register();
        String firstToken = identityProvider.currentEmailToken(EMAIL);

        mockMvc.perform(post("/api/v1/registrations/verification-resends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());

        String secondToken = identityProvider.currentEmailToken(EMAIL);
        assertNotEquals(firstToken, secondToken, "a resend must mint a new link");

        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(firstToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
        assertEquals(ClientStatus.PENDING_VERIFICATION, storedClient(clientId).getStatus());

        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(ClientStatus.ACTIVE, storedClient(clientId).getStatus());
    }

    @Test
    @DisplayName("HU-021 AC 'recuperación de contraseña con enlace de un solo uso': the recovery link is single use")
    void passwordRecoveryLinkIsRejectedOnSecondUse() throws Exception {
        registerAndConfirm();

        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());

        String tokenHash = identityProvider.currentRecoveryToken(EMAIL);

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, "N3w!StrongPass")))
                .andExpect(status().isNoContent());
        assertTrue(identityProvider.isTokenRedeemed(tokenHash), "the provider must have burned the recovery token");

        // Replaying the same link must not let anyone set the password again
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, "An0ther!Pass")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));

        // And the password that counts is the one set by the single valid redemption
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("An0ther!Pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("N3w!StrongPass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("HU-021 AC: a recovery link rejected by the password policy is NOT consumed")
    void weakPasswordDoesNotBurnTheRecoveryLink() throws Exception {
        registerAndConfirm();
        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());
        String tokenHash = identityProvider.currentRecoveryToken(EMAIL);

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, "todolowercase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_WEAK"));

        assertFalse(identityProvider.isTokenRedeemed(tokenHash),
                "a request stopped by the local policy must leave the link usable");

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(tokenHash, "N3w!StrongPass")))
                .andExpect(status().isNoContent());
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

    private ClientEntity storedClient(UUID clientId) {
        return clientJpaRepository.findById(clientId).orElseThrow();
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
