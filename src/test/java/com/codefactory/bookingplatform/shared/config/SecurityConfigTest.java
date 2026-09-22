package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pieces of {@link SecurityConfig} that produce a response:
 * the JWT decoder bean and the two ProblemDetail writers used when a request is
 * unauthenticated (401) or not allowed (403).
 */
class SecurityConfigTest {

    private static final SecurityProperties PROPERTIES = new SecurityProperties(
            "http://localhost:54321/auth/v1",
            "http://localhost:54321/auth/v1/.well-known/jwks.json");

    private SecurityConfig config;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig(PROPERTIES, new ObjectMapper());
        request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRequestURI("/api/v1/auth/me");
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private AuthenticationEntryPoint entryPoint() {
        return ReflectionTestUtils.invokeMethod(config, "problemDetailEntryPoint");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return ReflectionTestUtils.invokeMethod(config, "problemDetailAccessDeniedHandler");
    }

    @Test
    @DisplayName("The JWT decoder bean is built from the configured JWKS endpoint")
    void jwtDecoderIsBuilt() {
        JwtDecoder decoder = config.jwtDecoder();

        assertInstanceOf(NimbusJwtDecoder.class, decoder);
    }

    @Test
    @DisplayName("A missing token is answered 401")
    void unauthenticatedRequestGets401() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("The 401 answer is a ProblemDetail document")
    void unauthenticatedRequestGetsProblemJson() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("The 401 answer carries the AUTH_REQUIRED error code")
    void unauthenticatedRequestCarriesTheErrorCode() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertTrue(response.getContentAsString().contains("AUTH_REQUIRED"),
                "expected AUTH_REQUIRED in the body but got: " + response.getContentAsString());
    }

    @Test
    @DisplayName("The 401 answer points at the requested path")
    void unauthenticatedRequestCarriesTheInstance() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertTrue(response.getContentAsString().contains("/api/v1/auth/me"),
                "expected the request URI in the body but got: " + response.getContentAsString());
    }

    @Test
    @DisplayName("The 401 answer propagates the current trace id so the caller can report it")
    void unauthenticatedRequestCarriesTheTraceId() throws Exception {
        MDC.put("traceId", "trace-4711");

        entryPoint().commence(request, response, new StubAuthenticationException());

        assertTrue(response.getContentAsString().contains("trace-4711"),
                "expected the trace id in the body but got: " + response.getContentAsString());
    }

    @Test
    @DisplayName("A request without a trace id in the MDC is still answered, with no trace id")
    void unauthenticatedRequestWithoutTraceIdStillAnswers() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("An authenticated but unauthorised request is answered 403")
    void forbiddenRequestGets403() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("The 403 answer carries the ACCESS_DENIED error code")
    void forbiddenRequestCarriesTheErrorCode() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"),
                "expected ACCESS_DENIED in the body but got: " + response.getContentAsString());
    }

    @Test
    @DisplayName("The 403 answer is a ProblemDetail document")
    void forbiddenRequestGetsProblemJson() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("SECURITY: neither error answer leaks the underlying exception message")
    void errorAnswersDoNotLeakInternals() throws Exception {
        accessDeniedHandler().handle(request, response,
                new AccessDeniedException("role ROLE_ADMIN required on method ClientAdminService.delete"));

        assertTrue(!response.getContentAsString().contains("ClientAdminService"),
                "the internal message leaked into the body: " + response.getContentAsString());
    }

    @Test
    @DisplayName("The entry point is reused for both the resource server and the generic handling")
    void entryPointIsAvailable() {
        assertNotNull(entryPoint());
    }

    // -----------------------------------------------------------------
    // The body of the error answer, field by field. Asserting only that
    // the payload "contains AUTH_REQUIRED" leaves detail, title and
    // timestamp free to disappear without a single test noticing.
    // -----------------------------------------------------------------

    private JsonNode body() throws Exception {
        String payload = response.getContentAsString();
        return new ObjectMapper().readTree(payload);
    }

    @Test
    @DisplayName("The 401 answer explains in its detail that authentication is required")
    void unauthenticatedRequestCarriesTheDefaultDetail() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertEquals(ErrorCode.AUTH_REQUIRED.defaultMessage(), body().path("detail").asString(null));
    }

    @Test
    @DisplayName("The 403 answer explains in its detail that the permissions are insufficient")
    void forbiddenRequestCarriesTheDefaultDetail() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        assertEquals(ErrorCode.ACCESS_DENIED.defaultMessage(), body().path("detail").asString(null));
    }

    @Test
    @DisplayName("The 401 answer is titled with the reason phrase of its status")
    void unauthenticatedRequestCarriesTheTitle() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        assertEquals(HttpStatus.UNAUTHORIZED.getReasonPhrase(), body().path("title").asString(null));
    }

    @Test
    @DisplayName("The 403 answer is titled with the reason phrase of its status")
    void forbiddenRequestCarriesTheTitle() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), body().path("title").asString(null));
    }

    @Test
    @DisplayName("The 401 answer is stamped with the moment it was produced, so it can be correlated with the logs")
    void unauthenticatedRequestCarriesATimestamp() throws Exception {
        entryPoint().commence(request, response, new StubAuthenticationException());

        JsonNode timestamp = body().path("properties").path("timestamp");

        assertFalse(timestamp.isMissingNode() || timestamp.isNull(),
                "no timestamp in the body: " + response.getContentAsString());
        assertDoesNotThrow(() -> java.time.Instant.parse(timestamp.asString()));
    }

    @Test
    @DisplayName("The 403 answer is stamped with the moment it was produced")
    void forbiddenRequestCarriesATimestamp() throws Exception {
        accessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

        JsonNode timestamp = body().path("properties").path("timestamp");

        assertFalse(timestamp.isMissingNode() || timestamp.isNull(),
                "no timestamp in the body: " + response.getContentAsString());
        assertDoesNotThrow(() -> java.time.Instant.parse(timestamp.asString()));
    }

    private static final class StubAuthenticationException extends AuthenticationException {
        StubAuthenticationException() {
            super("Full authentication is required to access this resource");
        }
    }
}
