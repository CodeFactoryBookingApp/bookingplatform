package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the two ProblemDetail writers of {@link SecurityConfig}, used when a request is
 * unauthenticated (401) or not allowed (403). What the filter chain already proves end to end
 * (status, errorCode, title and instance of the 401) lives in {@link SecurityConfigWebTest};
 * the decoder lives in {@link SecurityConfigJwtDecoderTest}.
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

    private JsonNode body() throws Exception {
        String payload = response.getContentAsString();
        return new ObjectMapper().readTree(payload);
    }

    @Test
    @DisplayName("The 401 answer is a ProblemDetail document")
    void unauthenticatedRequestGetsProblemJson() throws Exception {
        // Arrange
        AuthenticationEntryPoint entryPoint = entryPoint();

        // Act
        entryPoint.commence(request, response, new StubAuthenticationException());

        // Assert
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("The 401 answer propagates the current trace id so the caller can report it")
    void unauthenticatedRequestCarriesTheTraceId() throws Exception {
        // Arrange
        MDC.put("traceId", "trace-4711");
        AuthenticationEntryPoint entryPoint = entryPoint();

        // Act
        entryPoint.commence(request, response, new StubAuthenticationException());

        // Assert
        assertEquals("trace-4711", body().path("properties").path("traceId").asString(null));
    }

    @Test
    @DisplayName("An authenticated but unauthorised request is answered 403")
    void forbiddenRequestGets403() throws Exception {
        // Arrange
        AccessDeniedHandler handler = accessDeniedHandler();

        // Act
        handler.handle(request, response, new AccessDeniedException("nope"));

        // Assert
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("The 403 answer carries the ACCESS_DENIED error code")
    void forbiddenRequestCarriesTheErrorCode() throws Exception {
        // Arrange
        AccessDeniedHandler handler = accessDeniedHandler();

        // Act
        handler.handle(request, response, new AccessDeniedException("nope"));

        // Assert
        assertEquals("ACCESS_DENIED", body().path("properties").path("errorCode").asString(null));
    }

    @Test
    @DisplayName("SECURITY: the 403 answer does not leak the message of the underlying exception")
    void errorAnswersDoNotLeakInternals() throws Exception {
        // Arrange
        AccessDeniedHandler handler = accessDeniedHandler();
        AccessDeniedException internal =
                new AccessDeniedException("role ROLE_ADMIN required on method ClientAdminService.delete");

        // Act
        handler.handle(request, response, internal);

        // Assert
        assertFalse(response.getContentAsString().contains("ClientAdminService"),
                "the internal message leaked into the body: " + response.getContentAsString());
    }

    @Test
    @DisplayName("The 401 type URI does not depend on the JVM default locale (Turkish dotless-i trap)")
    void unauthenticatedTypeUriIsLocaleIndependent() throws Exception {
        // Arrange
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
        AuthenticationEntryPoint entryPoint = entryPoint();
        try {
            // Act
            entryPoint.commence(request, response, new StubAuthenticationException());

            // Assert
            assertTrue(response.getContentAsString().contains("/errors/auth_required"),
                    "expected the ASCII type URI but got: " + response.getContentAsString());
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("The 401 answer explains in its detail that authentication is required")
    void unauthenticatedRequestCarriesTheDefaultDetail() throws Exception {
        // Arrange
        AuthenticationEntryPoint entryPoint = entryPoint();

        // Act
        entryPoint.commence(request, response, new StubAuthenticationException());

        // Assert
        assertEquals(ErrorCode.AUTH_REQUIRED.defaultMessage(), body().path("detail").asString(null));
    }

    @Test
    @DisplayName("The 403 answer explains in its detail that the permissions are insufficient")
    void forbiddenRequestCarriesTheDefaultDetail() throws Exception {
        // Arrange
        AccessDeniedHandler handler = accessDeniedHandler();

        // Act
        handler.handle(request, response, new AccessDeniedException("nope"));

        // Assert
        assertEquals(ErrorCode.ACCESS_DENIED.defaultMessage(), body().path("detail").asString(null));
    }

    @Test
    @DisplayName("The 401 answer is stamped with the moment it was produced, so it can be correlated with the logs")
    void unauthenticatedRequestCarriesATimestamp() throws Exception {
        // Arrange
        AuthenticationEntryPoint entryPoint = entryPoint();

        // Act
        entryPoint.commence(request, response, new StubAuthenticationException());

        // Assert
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
