package com.codefactory.bookingplatform.auth.api;

import com.codefactory.bookingplatform.auth.application.LoginUseCase;
import com.codefactory.bookingplatform.auth.application.LogoutUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordRecoveryUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordResetUseCase;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice for {@link AuthController}: HTTP contract of every endpoint with the
 * use cases mocked. Security filters are off; the authenticated endpoints get
 * their {@link JwtAuthenticationToken} injected as the request principal, which
 * is exactly what the argument resolver reads at runtime.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebTest {

    private static final String SUBJECT = "11111111-2222-3333-4444-555555555555";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String RECOVERY = "/api/v1/auth/password-recovery-requests";
    private static final String RESETS = "/api/v1/auth/password-resets";
    private static final String ME = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private PasswordRecoveryUseCase passwordRecoveryUseCase;

    @MockitoBean
    private PasswordResetUseCase passwordResetUseCase;

    private static Jwt jwt(String tokenValue, String email) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "ES256")
                .subject(SUBJECT)
                .claim("email", email)
                .issuedAt(Instant.parse("2026-09-22T10:00:00Z"))
                .expiresAt(Instant.parse("2026-09-22T11:00:00Z"))
                .build();
    }

    private static JwtAuthenticationToken principal(String tokenValue, String... authorities) {
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();
        return new JwtAuthenticationToken(jwt(tokenValue, "ana.perez@example.com"), granted);
    }

    private static String loginPayload() {
        return "{\"email\": \"ana.perez@example.com\", \"password\": \"Str0ng!Pass\"}";
    }

    private static RequestBuilder loginRequest() {
        return post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginPayload());
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("POST /login returns accessToken, refreshToken, tokenType and expiresIn")
    void loginReturnsTheTokenBundle() throws Exception {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("access-token", "refresh-token", "bearer", 3600L));

        // Act
        ResultActions result = mockMvc.perform(loginRequest());

        // Assert
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("SECURITY: the login response never echoes the submitted password")
    void loginResponseDoesNotLeakThePassword() throws Exception {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("access-token", "refresh-token", "bearer", 3600L));

        // Act
        ResultActions result = mockMvc.perform(loginRequest());

        // Assert
        String body = result.andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Str0ng!Pass"), "the plain password leaked into the login response");
        assertFalse(body.toLowerCase().contains("password"), "a password field leaked into the login response");
    }

    @Test
    @DisplayName("POST /login forwards the credentials to the use case verbatim")
    void loginForwardsTheCredentials() throws Exception {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("access-token", "refresh-token", "bearer", 3600L));

        // Act
        ResultActions result = mockMvc.perform(loginRequest());

        // Assert
        result.andExpect(status().isOk());
        verify(loginUseCase).login("ana.perez@example.com", "Str0ng!Pass");
    }

    @ParameterizedTest(name = "{0} from the login use case becomes HTTP {1}")
    @CsvSource({
            "INVALID_CREDENTIALS,  401",
            "EMAIL_NOT_CONFIRMED,  403",
            "UPSTREAM_AUTH_ERROR,  502",
            "RATE_LIMITED,         429"})
    @DisplayName("Login business failures are mapped to their declared status and errorCode")
    void loginFailuresAreMapped(String errorCode, int expectedStatus) throws Exception {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.valueOf(errorCode)));

        // Act
        ResultActions result = mockMvc.perform(loginRequest());

        // Assert
        result.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    @Test
    @DisplayName("A locked account answers 429 and tells the caller how long to wait")
    void lockedAccountExposesRetryAfterMinutes() throws Exception {
        // Arrange
        when(loginUseCase.login(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                        ErrorCode.ACCOUNT_LOCKED.defaultMessage(), Map.of("retryAfterMinutes", "15")));

        // Act
        ResultActions result = mockMvc.perform(loginRequest());

        // Assert
        result.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterMinutes").value("15"));
    }

    @ParameterizedTest(name = "login with email [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   ", "not-an-email", "@example.com"})
    void loginRejectsInvalidEmails(String email) throws Exception {
        // Arrange
        RequestBuilder request = post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"Str0ng!Pass\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").isNotEmpty());
        verify(loginUseCase, never()).login(anyString(), anyString());
    }

    @ParameterizedTest(name = "login with password [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   "})
    void loginRejectsBlankPasswords(String password) throws Exception {
        // Arrange
        RequestBuilder request = post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana.perez@example.com\", \"password\": \"" + password + "\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").value("password is required"));
        verify(loginUseCase, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("A missing password field is rejected with 400")
    void loginRejectsMissingPassword() throws Exception {
        // Arrange
        RequestBuilder request = post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana.perez@example.com\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.password").value("password is required"));
        verify(loginUseCase, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("A syntactically broken login body is answered 400 VALIDATION_ERROR")
    void loginRejectsBrokenJson() throws Exception {
        // Arrange
        RequestBuilder request = post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana@example.com\"");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    // ----------------------------------------------------------------- logout

    @Test
    @DisplayName("POST /logout answers 204 with an empty body and hands the raw bearer token to the use case")
    void logoutForwardsTheRawToken() throws Exception {
        // Arrange
        RequestBuilder request = post(LOGOUT).principal(principal("the-access-token", "ROLE_CLIENT"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(logoutUseCase).logout("the-access-token");
    }

    @Test
    @DisplayName("A provider failure during logout surfaces as 502 UPSTREAM_AUTH_ERROR")
    void logoutUpstreamFailureIsMappedTo502() throws Exception {
        // Arrange
        doThrow(new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR))
                .when(logoutUseCase).logout(anyString());
        RequestBuilder request = post(LOGOUT).principal(principal("the-access-token", "ROLE_CLIENT"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("UPSTREAM_AUTH_ERROR"));
    }

    // --------------------------------------------------------- recovery/reset

    @Test
    @DisplayName("POST /password-recovery-requests answers 202 Accepted with an empty body")
    void recoveryAnswers202() throws Exception {
        // Arrange
        RequestBuilder request = post(RECOVERY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana.perez@example.com\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isAccepted())
                .andExpect(content().string(""));
        verify(passwordRecoveryUseCase).requestRecovery("ana.perez@example.com");
    }

    @ParameterizedTest(name = "recovery with email [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   ", "not-an-email", "@example.com"})
    void recoveryRejectsInvalidEmails(String email) throws Exception {
        // Arrange
        RequestBuilder request = post(RECOVERY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(passwordRecoveryUseCase, never()).requestRecovery(anyString());
    }

    @Test
    @DisplayName("POST /password-resets answers 204 No Content with an empty body")
    void resetAnswers204() throws Exception {
        // Arrange
        RequestBuilder request = post(RESETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\": \"pkce_1a2b3c\", \"newPassword\": \"N3w!Password\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(passwordResetUseCase).resetPassword("pkce_1a2b3c", "N3w!Password");
    }

    @Test
    @DisplayName("A weak new password is answered 400 PASSWORD_TOO_WEAK with the violations")
    void resetWithWeakPasswordIsRejected() throws Exception {
        // Arrange
        doThrow(new BusinessException(ErrorCode.PASSWORD_TOO_WEAK,
                ErrorCode.PASSWORD_TOO_WEAK.defaultMessage(), Map.of("violations", "at least one digit")))
                .when(passwordResetUseCase).resetPassword(anyString(), anyString());
        RequestBuilder request = post(RESETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\": \"pkce_1a2b3c\", \"newPassword\": \"onlyletters\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_WEAK"))
                .andExpect(jsonPath("$.details.violations").value("at least one digit"));
    }

    @Test
    @DisplayName("A consumed recovery token is answered 400 VERIFICATION_TOKEN_INVALID")
    void resetWithConsumedTokenIsRejected() throws Exception {
        // Arrange
        doThrow(new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID))
                .when(passwordResetUseCase).resetPassword(anyString(), anyString());
        RequestBuilder request = post(RESETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\": \"already-used\", \"newPassword\": \"N3w!Password\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @ParameterizedTest(name = "reset with a {0} character password is rejected with 400")
    @ValueSource(ints = {1, 7, 73})
    void resetRejectsPasswordsOutsideTheSizeRange(int length) throws Exception {
        // Arrange
        RequestBuilder request = post(RESETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\": \"pkce_1a2b3c\", \"newPassword\": \"" + "a".repeat(length) + "\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword")
                        .value("newPassword must be between 8 and 72 characters"));
        verify(passwordResetUseCase, never()).resetPassword(anyString(), anyString());
    }

    @ParameterizedTest(name = "reset with tokenHash [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   "})
    void resetRejectsBlankTokenHash(String tokenHash) throws Exception {
        // Arrange
        RequestBuilder request = post(RESETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenHash\": \"" + tokenHash + "\", \"newPassword\": \"N3w!Password\"}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.tokenHash").value("tokenHash is required"));
        verify(passwordResetUseCase, never()).resetPassword(anyString(), anyString());
    }

    // --------------------------------------------------------------------- me

    @Test
    @DisplayName("GET /me answers 200 with id, email and the role taken from the granted authority")
    void meReturnsTheProfile() throws Exception {
        // Arrange
        RequestBuilder request = get(ME).principal(principal("access-token", "ROLE_CLIENT"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SUBJECT))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("GET /me ignores authorities that are not roles and reports role null")
    void meIgnoresNonRoleAuthorities() throws Exception {
        // Arrange
        RequestBuilder request = get(ME).principal(principal("access-token", "SCOPE_read", "SCOPE_write"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    @DisplayName("GET /me picks the first ROLE_ authority when several are present")
    void mePicksTheFirstRoleAuthority() throws Exception {
        // Arrange
        RequestBuilder request = get(ME)
                .principal(principal("access-token", "SCOPE_read", "ROLE_PROVIDER", "ROLE_CLIENT"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(jsonPath("$.role").value("PROVIDER"));
    }

    @Test
    @DisplayName("SECURITY: GET /me never returns the raw access token")
    void meDoesNotLeakTheAccessToken() throws Exception {
        // Arrange
        RequestBuilder request = get(ME).principal(principal("super-secret-access-token", "ROLE_CLIENT"));

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        String body = result.andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("super-secret-access-token"), "the access token leaked into the /me response");
    }
}
