package com.codefactory.bookingplatform.auth.api;

import com.codefactory.bookingplatform.auth.api.dto.LoginRequest;
import com.codefactory.bookingplatform.auth.api.dto.LoginResponse;
import com.codefactory.bookingplatform.auth.api.dto.MeResponse;
import com.codefactory.bookingplatform.auth.api.dto.PasswordRecoveryRequest;
import com.codefactory.bookingplatform.auth.api.dto.PasswordResetRequest;
import com.codefactory.bookingplatform.auth.application.LoginUseCase;
import com.codefactory.bookingplatform.auth.application.LogoutUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordRecoveryUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordResetUseCase;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The controller is a delegating layer: every endpoint must reach its own use case with the values
 * the caller sent, and nothing else. These are plain unit tests without an application context, so
 * each endpoint is exercised on its own instead of relying on a single cached Spring context to
 * cover the whole class.
 */
class AuthControllerTest {

    private static final UUID SUBJECT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final LoginUseCase loginUseCase = mock(LoginUseCase.class);
    private final LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
    private final PasswordRecoveryUseCase passwordRecoveryUseCase = mock(PasswordRecoveryUseCase.class);
    private final PasswordResetUseCase passwordResetUseCase = mock(PasswordResetUseCase.class);

    private final AuthController controller = new AuthController(
            loginUseCase, logoutUseCase, passwordRecoveryUseCase, passwordResetUseCase);

    private JwtAuthenticationToken authentication(String tokenValue, String... authorities) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "ES256")
                .subject(SUBJECT.toString())
                .claim("email", "ana.perez@example.com")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).map(a -> (org.springframework.security.core.GrantedAuthority) a).toList());
    }

    @Test
    @DisplayName("Login forwards the submitted credentials to the login use case")
    void loginForwardsCredentials() {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("access", "refresh", "bearer", 3600L));
        LoginRequest request = new LoginRequest("ana.perez@example.com", "Str0ng!Pass");

        // Act
        controller.login(request);

        // Assert
        verify(loginUseCase).login("ana.perez@example.com", "Str0ng!Pass");
    }

    @Test
    @DisplayName("Login answers with the tokens the provider issued, not with a placeholder")
    void loginReturnsTheIssuedTokens() {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("access", "refresh", "bearer", 3600L));
        LoginRequest request = new LoginRequest("ana.perez@example.com", "Str0ng!Pass");

        // Act
        LoginResponse response = controller.login(request);

        // Assert
        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals("bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
    }

    @Test
    @DisplayName("Logout invalidates the raw bearer token of the caller, not the subject id")
    void logoutForwardsTheBearerToken() {
        // Arrange
        JwtAuthenticationToken authentication = authentication("the-access-token");

        // Act
        controller.logout(authentication);

        // Assert
        verify(logoutUseCase).logout("the-access-token");
    }

    @Test
    @DisplayName("A password recovery request reaches the recovery use case with the submitted email")
    void recoveryForwardsTheEmail() {
        // Arrange
        PasswordRecoveryRequest request = new PasswordRecoveryRequest("ana.perez@example.com");

        // Act
        controller.requestPasswordRecovery(request);

        // Assert
        verify(passwordRecoveryUseCase).requestRecovery("ana.perez@example.com");
    }

    @Test
    @DisplayName("A password reset forwards both the one-time token hash and the new password")
    void resetForwardsTokenAndPassword() {
        // Arrange
        PasswordResetRequest request = new PasswordResetRequest("token-hash", "NewSecret123!");

        // Act
        controller.resetPassword(request);

        // Assert
        verify(passwordResetUseCase).resetPassword("token-hash", "NewSecret123!");
    }

    @Test
    @DisplayName("The profile endpoint reads id and email from the token claims")
    void meReadsTheTokenClaims() {
        // Arrange
        JwtAuthenticationToken authentication = authentication("token", "ROLE_CLIENT");

        // Act
        MeResponse response = controller.me(authentication);

        // Assert
        assertEquals(SUBJECT, response.id());
        assertEquals("ana.perez@example.com", response.email());
    }

    @Test
    @DisplayName("The profile endpoint publishes the role without its ROLE_ prefix")
    void meStripsTheRolePrefix() {
        // Arrange
        JwtAuthenticationToken authentication = authentication("token", "ROLE_CLIENT");

        // Act
        MeResponse response = controller.me(authentication);

        // Assert
        assertEquals("CLIENT", response.role());
    }

    @Test
    @DisplayName("A token that grants no ROLE_ authority yields no role at all")
    void meReportsNoRoleWhenNoneIsGranted() {
        // Arrange
        JwtAuthenticationToken authentication = authentication("token", "SCOPE_openid");

        // Act
        MeResponse response = controller.me(authentication);

        // Assert
        assertNull(response.role());
    }
}
