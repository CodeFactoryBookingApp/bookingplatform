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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "HU-021 - login, logout, password recovery (Supabase Auth backed)")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final PasswordRecoveryUseCase passwordRecoveryUseCase;
    private final PasswordResetUseCase passwordResetUseCase;

    public AuthController(LoginUseCase loginUseCase,
                          LogoutUseCase logoutUseCase,
                          PasswordRecoveryUseCase passwordRecoveryUseCase,
                          PasswordResetUseCase passwordResetUseCase) {
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.passwordRecoveryUseCase = passwordRecoveryUseCase;
        this.passwordResetUseCase = passwordResetUseCase;
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password",
            description = "Verifies credentials against the identity provider. "
                    + "Repeated failures lock the account temporarily (HTTP 429 with retryAfterMinutes).")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthTokens tokens = loginUseCase.login(request.email(), request.password());
        return LoginResponse.from(tokens);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and invalidate the current session")
    public void logout(JwtAuthenticationToken authentication) {
        logoutUseCase.logout(authentication.getToken().getTokenValue());
    }

    @PostMapping("/password-recovery-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password recovery email",
            description = "Always 202 to avoid user enumeration. The provider sends a one-time link if the email exists.")
    public void requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        passwordRecoveryUseCase.requestRecovery(request.email());
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset password with the one-time recovery token")
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetUseCase.resetPassword(request.tokenHash(), request.newPassword());
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user profile (id, email, role from JWT)")
    public MeResponse me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
        return new MeResponse(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), role);
    }
}
