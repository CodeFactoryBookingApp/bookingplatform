package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.auth.api.AuthController;
import com.codefactory.bookingplatform.auth.application.LoginUseCase;
import com.codefactory.bookingplatform.auth.application.LogoutUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordRecoveryUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordResetUseCase;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.identity.api.RegistrationController;
import com.codefactory.bookingplatform.identity.application.ConfirmEmailUseCase;
import com.codefactory.bookingplatform.identity.application.RegisterClientUseCase;
import com.codefactory.bookingplatform.identity.application.RegistrationOutcome;
import com.codefactory.bookingplatform.identity.application.ResendVerificationUseCase;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.shared.observability.TraceIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain itself, exercised end to end through MockMvc with
 * the real {@link SecurityConfig}: which endpoints are public, what an
 * unauthenticated call gets back, and how the JWT is turned into authorities.
 */
@WebMvcTest(controllers = {AuthController.class, RegistrationController.class})
@Import({SecurityConfig.class, SupabaseJwtAuthConverter.class, SecurityConfigWebTest.CorsForTest.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "app.security.jwt-issuer=http://localhost:54321/auth/v1",
        "app.security.jwks-uri=http://localhost:54321/auth/v1/.well-known/jwks.json"})
class SecurityConfigWebTest {

    private static final String SUBJECT = "11111111-2222-3333-4444-555555555555";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private PasswordRecoveryUseCase passwordRecoveryUseCase;

    @MockitoBean
    private PasswordResetUseCase passwordResetUseCase;

    @MockitoBean
    private RegisterClientUseCase registerClientUseCase;

    @MockitoBean
    private ResendVerificationUseCase resendVerificationUseCase;

    @MockitoBean
    private ConfirmEmailUseCase confirmEmailUseCase;

    private void stubToken(String tokenValue, Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "ES256")
                .subject(SUBJECT)
                .claim("email", "ana.perez@example.com")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600));
        extraClaims.forEach(builder::claim);
        when(jwtDecoder.decode(tokenValue)).thenReturn(builder.build());
    }

    @ParameterizedTest(name = "{1} {0} is public and is not answered 401")
    @CsvSource({
            "/api/v1/auth/login,                      POST",
            "/api/v1/auth/password-recovery-requests, POST",
            "/api/v1/auth/password-resets,            POST",
            "/api/v1/registrations,                   POST",
            "/api/v1/registrations/verification-resends, POST",
            "/api/v1/registrations/email-verifications, POST"})
    @DisplayName("The declared public endpoints are reachable without a token")
    void publicEndpointsDoNotRequireAToken(String path, String method) throws Exception {
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("a", "r", "bearer", 3600L));
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(UUID.fromString(SUBJECT), "ana.perez@example.com",
                        ClientStatus.PENDING_VERIFICATION));
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(UUID.fromString(SUBJECT), "ana.perez@example.com",
                        ClientStatus.ACTIVE));

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    @DisplayName("CSRF is disabled, so a POST without a CSRF token is not answered 403")
    void csrfIsDisabledForStatelessApiCalls() throws Exception {
        when(loginUseCase.login(anyString(), anyString()))
                .thenReturn(new AuthTokens("a", "r", "bearer", 3600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana.perez@example.com\", \"password\": \"Str0ng!Pass\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} requires authentication and is answered 401")
    @ValueSource(strings = {"/api/v1/auth/me"})
    @DisplayName("A protected endpoint without a token is answered 401 AUTH_REQUIRED")
    void protectedEndpointWithoutTokenIs401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("POST /logout without a token is answered 401, not 204")
    void logoutWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("The 401 answer is a ProblemDetail pointing at the requested path")
    void unauthorizedAnswerIsAProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.type")
                        .value("https://bookingplatform.codefactory.com/errors/auth_required"));
    }

    @Test
    @DisplayName("An unauthenticated call still gets a trace id header it can quote in a ticket")
    void unauthorizedAnswerCarriesTheTraceIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("An unparsable bearer token is answered 401, never 500")
    void invalidTokenIs401() throws Exception {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("malformed"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A valid token reaches the endpoint and the role comes from app_metadata")
    void validTokenIsAcceptedAndRoleComesFromAppMetadata() throws Exception {
        stubToken("valid-token", Map.of("app_metadata", Map.of("role", "client")));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("PRIVILEGE ESCALATION GUARD: a role planted in user_metadata grants no role at all")
    void roleInUserMetadataIsIgnoredEndToEnd() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_metadata", Map.of("role", "admin"));
        stubToken("tampered-token", claims);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer tampered-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    @DisplayName("PRIVILEGE ESCALATION GUARD: app_metadata wins over a tampered user_metadata")
    void appMetadataWinsEndToEnd() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("app_metadata", Map.of("role", "client"));
        claims.put("user_metadata", Map.of("role", "admin"));
        stubToken("mixed-token", claims);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer mixed-token"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("An authenticated logout goes through and answers 204")
    void authenticatedLogoutAnswers204() throws Exception {
        stubToken("valid-token", Map.of("app_metadata", Map.of("role", "client")));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("An unmapped path still requires authentication, so nothing is public by accident")
    void unmappedPathsAreNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/internal/whatever"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The application declares {@code http.cors(...)} but publishes no
     * {@link CorsConfigurationSource} bean of its own, so in production the CORS
     * step is inert. This bean is supplied here to prove that the filter chain
     * does wire the CORS step up when a configuration exists.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class CorsForTest {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration cors = new CorsConfiguration();
            cors.addAllowedOrigin("https://app.example.com");
            cors.addAllowedMethod("*");
            cors.addAllowedHeader("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", cors);
            return source;
        }
    }

    @Test
    @DisplayName("The chain honours the application CORS configuration, so the browser front end is answered a preflight")
    void corsPreflightIsAnswered() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    @DisplayName("The API is stateless: not even an unauthenticated call leaves an HTTP session behind")
    void noHttpSessionIsCreated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();

        assertNull(result.getRequest().getSession(false), "the request created an HTTP session");
    }

    @Test
    @DisplayName("The 401 body explains that authentication is required and is stamped with the moment it happened")
    void unauthorizedAnswerCarriesDetailAndTimestamp() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("A token rejected by the decoder is answered with the platform ProblemDetail, not with the Spring default")
    void invalidTokenAnswerIsOurProblemDetail() throws Exception {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("malformed"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"));
    }
}
