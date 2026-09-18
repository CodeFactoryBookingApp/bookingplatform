package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("HU-021 AC: successful login returns the provider tokens")
    void loginSuccessfully() throws Exception {
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenReturn(new AuthTokens("access-token", "refresh-token", "bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("HU-021 AC: invalid credentials are rejected with 401 and recorded as failure")
    void loginWithInvalidCredentials() throws Exception {
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        verify(identityProviderPort).requestPasswordToken(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-001/HU-021 AC: unverified client cannot log in (email not confirmed)")
    void loginWithUnconfirmedEmail() throws Exception {
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.EMAIL_NOT_CONFIRMED, "not confirmed"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("HU-021 AC: account is temporarily locked after 5 failed attempts")
    void lockAfterRepeatedFailures() throws Exception {
        when(identityProviderPort.requestPasswordToken(anyString(), anyString()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "bad"));

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_PAYLOAD))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_PAYLOAD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterMinutes").value("15"));

        verify(identityProviderPort, times(5)).requestPasswordToken(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-021 AC: logout revokes the session at the identity provider")
    void logoutRevokesSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(jwt()
                                .jwt(builder -> builder.tokenValue("user-access-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isNoContent());

        verify(identityProviderPort).signOut("user-access-token");
    }

    @Test
    @DisplayName("HU-021 AC: /me requires authentication and returns the role from app_metadata")
    void meEndpointIsProtectedAndReturnsRole() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));

        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject(userId.toString())
                                        .claim("email", "ana.perez@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("HU-021 AC: password recovery always answers 202 (no user enumeration)")
    void passwordRecoveryIsAcceptedEvenForUnknownEmail() throws Exception {
        org.mockito.Mockito.doThrow(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "unknown"))
                .when(identityProviderPort).sendPasswordRecovery("unknown@example.com");

        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.perez@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-021 AC: password reset enforces the policy and consumes the one-time token")
    void passwordResetFlow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"recovery-token\",\"newPassword\":\"todolowercase\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_WEAK"));

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"recovery-token\",\"newPassword\":\"N3w!StrongPass\"}"))
                .andExpect(status().isNoContent());

        verify(identityProviderPort).resetPasswordWithToken("recovery-token", "N3w!StrongPass");
    }

    @Test
    @DisplayName("Invalid or expired recovery token is rejected with 400")
    void passwordResetWithExpiredToken() throws Exception {
        org.mockito.Mockito.doThrow(new UpstreamAuthException(UpstreamAuthError.TOKEN_EXPIRED, "expired"))
                .when(identityProviderPort).resetPasswordWithToken(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"old-token\",\"newPassword\":\"N3w!StrongPass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Login with malformed payload fails bean validation")
    void loginValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(identityProviderPort, never()).requestPasswordToken(anyString(), anyString());
    }
}
