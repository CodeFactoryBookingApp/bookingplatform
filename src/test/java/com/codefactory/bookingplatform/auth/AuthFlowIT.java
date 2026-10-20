package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.infrastructure.persistence.LoginAttemptJpaRepository;
import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends PostgresIntegrationTestBase {

    private static final String LOGIN_PAYLOAD = """
            {"email":"ana.perez@example.com","password":"Str0ng!Pass"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginAttemptJpaRepository loginAttemptJpaRepository;

    @MockitoBean
    private IdentityProviderPort identityProviderPort;

    @BeforeEach
    void cleanDatabase() {
        loginAttemptJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("HU-021 AC: invalid credentials are rejected with 401 and recorded as failure")
    void loginWithInvalidCredentials() throws Exception {
        // Arrange
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "bad"));

        // Act
        ResultActions result = performLogin();

        // Assert
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        verify(identityProviderPort).requestPasswordToken(anyString(), anyString());
        assertEquals(1, loginAttemptJpaRepository.count(), "the failed attempt must be recorded");
    }

    @Test
    @DisplayName("HU-021 AC: account is temporarily locked after 5 failed attempts")
    void lockAfterRepeatedFailures() throws Exception {
        // Arrange
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "bad"));
        for (int attempt = 1; attempt <= 5; attempt++) {
            performLogin().andExpect(status().isUnauthorized());
        }

        // Act
        ResultActions sixthAttempt = performLogin();

        // Assert
        sixthAttempt.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterMinutes").value("15"));
        verify(identityProviderPort, times(5)).requestPasswordToken(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-021 AC: password recovery always answers 202 (no user enumeration)")
    void passwordRecoveryIsAcceptedEvenForUnknownEmail() throws Exception {
        // Arrange
        doThrow(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "unknown"))
                .when(identityProviderPort).sendPasswordRecovery("unknown@example.com");

        // Act
        ResultActions result = performPasswordRecovery("unknown@example.com");

        // Assert
        result.andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-021 AC: password recovery answers the same 202 for a known email")
    void passwordRecoveryIsAcceptedForKnownEmail() throws Exception {
        // Arrange
        doThrow(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "unknown"))
                .when(identityProviderPort).sendPasswordRecovery("unknown@example.com");

        // Act
        ResultActions result = performPasswordRecovery("ana.perez@example.com");

        // Assert
        result.andExpect(status().isAccepted());
        verify(identityProviderPort).sendPasswordRecovery("ana.perez@example.com");
    }

    @Test
    @DisplayName("HU-021 AC: password reset consumes the one-time token at the provider")
    void passwordResetFlow() throws Exception {
        // Arrange
        performPasswordReset("recovery-token", "todolowercase").andExpect(status().isBadRequest());

        // Act
        ResultActions result = performPasswordReset("recovery-token", "N3w!StrongPass");

        // Assert
        result.andExpect(status().isNoContent());
        verify(identityProviderPort).resetPasswordWithToken("recovery-token", "N3w!StrongPass");
    }

    @Test
    @DisplayName("Invalid or expired recovery token is rejected with 400")
    void passwordResetWithExpiredToken() throws Exception {
        // Arrange
        doThrow(new UpstreamAuthException(UpstreamAuthError.TOKEN_EXPIRED, "expired"))
                .when(identityProviderPort).resetPasswordWithToken(anyString(), anyString());

        // Act
        ResultActions result = performPasswordReset("old-token", "N3w!StrongPass");

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Login with malformed payload fails bean validation")
    void loginValidation() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(identityProviderPort, never()).requestPasswordToken(anyString(), anyString());
    }

    private ResultActions performLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_PAYLOAD));
    }

    private ResultActions performPasswordRecovery(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions performPasswordReset(String tokenHash, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\":\"" + tokenHash + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }
}
