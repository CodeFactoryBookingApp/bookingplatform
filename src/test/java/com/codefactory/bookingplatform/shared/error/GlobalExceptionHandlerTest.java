package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The global advice is the only place that turns an exception into the RFC 7807 body the client
 * sees, so every handler is invoked directly (no Spring context). The envelope built by the shared
 * {@code build} method (title, type, instance, traceId, timestamp) is checked once; each handler is
 * then checked only for what it decides itself: its error code, its detail and its details map.
 *
 * <p>Not pinned here on purpose: the "Internal Server Error" title of a 502 (BUG-027), the fixed
 * "Malformed request body" detail (BUG-026) and the advice failing on a request URI with characters
 * illegal in a URI (same family as BUG-029). Those are reported defects, not contracts.</p>
 */
class GlobalExceptionHandlerTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String REQUEST_URI = "/api/v1/auth/login";
    private static final String TYPE_PREFIX = "https://bookingplatform.codefactory.com/errors/";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        MDC.clear();
        request = new MockHttpServletRequest("POST", REQUEST_URI);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static Map<String, Object> propertiesOf(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        assertNotNull(properties, "The handler must always publish the errorCode and traceId properties");
        return properties;
    }

    /** Placeholder controller method used to build a real {@link MethodParameter}. */
    @SuppressWarnings("unused")
    private void controllerMethod(String payload) {
        // never invoked; only its signature is needed
    }

    private MethodArgumentNotValidException methodArgumentNotValid(List<ObjectError> errors) throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("controllerMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "payload");
        errors.forEach(binding::addError);
        return new MethodArgumentNotValidException(parameter, binding);
    }

    private HandlerMethodValidationException handlerMethodValidation(List<MessageSourceResolvable> errors) {
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        doReturn(errors).when(ex).getAllErrors();
        return ex;
    }

    // ----------------------------------------------------------------------------------------
    // handleBusiness
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Business rule rejections")
    class BusinessRuleRejections {

        @Test
        @DisplayName("A business rejection answers with the HTTP status declared by its error code")
        void statusComesFromTheErrorCode() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        }

        @Test
        @DisplayName("A business rejection publishes the machine-readable error code so the client can branch on it")
        void publishesTheErrorCode() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals("DUPLICATE_EMAIL", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("A business rejection points to the documentation page of its error code")
        void publishesTheTypeUri() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(URI.create(TYPE_PREFIX + "duplicate_email"), problem.getType());
        }

        @Test
        @DisplayName("The type URI does not depend on the JVM default locale (Turkish dotless-i trap)")
        void typeUriIsLocaleIndependent() {
            // Arrange
            java.util.Locale previous = java.util.Locale.getDefault();
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR);
            try {
                // Act
                ProblemDetail problem = handler.handleBusiness(ex, request);

                // Assert
                assertEquals(URI.create(TYPE_PREFIX + "validation_error"), problem.getType());
            } finally {
                java.util.Locale.setDefault(previous);
            }
        }

        @Test
        @DisplayName("A business rejection points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(URI.create(REQUEST_URI), problem.getInstance());
        }

        @Test
        @DisplayName("A business rejection carries the message of the exception as the human-readable detail")
        void detailIsTheExceptionMessage() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL, "ana@example.com is already registered");

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals("ana@example.com is already registered", problem.getDetail());
        }

        @Test
        @DisplayName("A 4xx business rejection uses the reason phrase of its own status as the title")
        void titleIsTheReasonPhraseForClientErrors() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(HttpStatus.CONFLICT.getReasonPhrase(), problem.getTitle());
        }

        @Test
        @DisplayName("The details property is published when the business rejection carries details")
        void detailsArePublishedWhenPresent() {
            // Arrange
            BusinessException ex = BusinessException.of(
                    ErrorCode.DUPLICATE_DOCUMENT, "already used", "documentNumber", "1017245896");

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(Map.of("documentNumber", "1017245896"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("The details property is omitted when the business rejection carries no details, so the body stays clean")
        void detailsAreOmittedWhenEmpty() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertFalse(propertiesOf(problem).containsKey("details"),
                    () -> "Expected no details property, got " + propertiesOf(problem));
        }
    }

    // ----------------------------------------------------------------------------------------
    // handleValidation
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Bean validation failures")
    class BeanValidationFailures {

        @Test
        @DisplayName("A body validation failure answers 400 with the validation error code")
        void bodyValidationAnswersBadRequest() throws Exception {
            // Arrange
            MethodArgumentNotValidException ex =
                    methodArgumentNotValid(List.of(new FieldError("payload", "email", "must not be blank")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
            assertEquals("VALIDATION_ERROR", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("A body validation failure lists each rejected field with its message")
        void fieldErrorsAreListedByFieldName() throws Exception {
            // Arrange
            MethodArgumentNotValidException ex = methodArgumentNotValid(List.of(
                    new FieldError("payload", "email", "must not be blank"),
                    new FieldError("payload", "password", "size must be between 8 and 72")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("email", "must not be blank", "password", "size must be between 8 and 72"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A body validation failure also lists cross-field errors under the name of the validated object")
        void globalErrorsAreListedByObjectName() throws Exception {
            // Arrange
            MethodArgumentNotValidException ex = methodArgumentNotValid(List.of(
                    new ObjectError("payload", "password and confirmation do not match")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("payload", "password and confirmation do not match"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure keys each error by its first resolvable code")
        void parameterErrorsAreKeyedByTheirFirstCode() {
            // Arrange
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[]{"Size.name"}, null, "size must be between 1 and 60")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("Size.name", "size must be between 1 and 60"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure without codes falls back to the positional argument name")
        void parameterErrorsWithoutCodesFallBackToThePosition() {
            // Arrange
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable((String[]) null, null, "must not be null")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("arg0", "must not be null"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure with an empty code array also falls back to the positional name")
        void parameterErrorsWithEmptyCodesFallBackToThePosition() {
            // Arrange
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[0], null, "must not be null")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("arg0", "must not be null"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure mixing named and unnamed errors keeps the index aligned with the argument position")
        void namedAndUnnamedParameterErrorsCoexist() {
            // Arrange
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[]{"Min.page"}, null, "must be at least 0"),
                    new DefaultMessageSourceResolvable((String[]) null, null, "must not be null")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(Map.of("Min.page", "must be at least 0", "arg1", "must not be null"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A validation failure with no collected errors still answers 400 with an empty details map")
        void emptyValidationStillAnswersBadRequest() {
            // Arrange
            HandlerMethodValidationException ex = handlerMethodValidation(List.of());

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
            assertEquals(Map.of(), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A validation failure never echoes the raw exception message, only the generic validation detail")
        void detailIsTheGenericValidationMessage() throws Exception {
            // Arrange
            MethodArgumentNotValidException ex =
                    methodArgumentNotValid(List.of(new FieldError("payload", "email", "must not be blank")));

            // Act
            ProblemDetail problem = handler.handleValidation(ex, request);

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR.defaultMessage(), problem.getDetail());
        }
    }

    // ----------------------------------------------------------------------------------------
    // handleUnreadable
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Malformed request body")
    class MalformedRequestBody {

        private final HttpMessageNotReadableException unreadable = mock(HttpMessageNotReadableException.class);

        @Test
        @DisplayName("An unparsable body answers 400 under the validation error code")
        void reportsTheValidationErrorCode() {
            // Act
            ProblemDetail problem = handler.handleUnreadable(unreadable, request);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
            assertEquals("VALIDATION_ERROR", propertiesOf(problem).get("errorCode"));
        }
    }

    // ----------------------------------------------------------------------------------------
    // handleAccessDenied
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Access denied")
    class AccessDenied {

        private final AccessDeniedException denied = new AccessDeniedException("Access is denied to /admin/users");

        @Test
        @DisplayName("A denied authorization answers 403 under the access denied error code")
        void reportsTheAccessDeniedErrorCode() {
            // Act
            ProblemDetail problem = handler.handleAccessDenied(denied, request);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
            assertEquals("ACCESS_DENIED", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("A denied authorization answers with the generic message, never with the resource the caller was probing")
        void detailDoesNotDiscloseTheProbedResource() {
            // Act
            ProblemDetail problem = handler.handleAccessDenied(denied, request);

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED.defaultMessage(), problem.getDetail());
            assertFalse(String.valueOf(problem.getDetail()).contains("/admin/users"));
        }
    }

    // ----------------------------------------------------------------------------------------
    // handleFramework - Spring MVC's own client errors keep their status (D9)
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Spring MVC client errors")
    class FrameworkErrors {

        private String errorCodeOf(ResponseEntity<ProblemDetail> response) {
            return String.valueOf(propertiesOf(response.getBody()).get("errorCode"));
        }

        @Test
        @DisplayName("An unsupported method answers 405 METHOD_NOT_ALLOWED and keeps the Allow header")
        void unsupportedMethodIs405WithAllow() {
            // Arrange
            Exception ex = new HttpRequestMethodNotSupportedException("GET", List.of("POST"));

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(405, response.getStatusCode().value());
            assertEquals(405, response.getBody().getStatus());
            assertEquals("METHOD_NOT_ALLOWED", errorCodeOf(response));
            assertTrue(response.getHeaders().getAllow().contains(HttpMethod.POST), response.getHeaders().toString());
        }

        @Test
        @DisplayName("An unsupported content type answers 415 UNSUPPORTED_MEDIA_TYPE and lists what is accepted")
        void unsupportedMediaTypeIs415WithAccept() {
            // Arrange
            Exception ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(415, response.getStatusCode().value());
            assertEquals("UNSUPPORTED_MEDIA_TYPE", errorCodeOf(response));
            assertTrue(response.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON), response.getHeaders().toString());
        }

        @Test
        @DisplayName("An unsupported Accept answers 406 NOT_ACCEPTABLE")
        void unacceptableMediaTypeIs406() {
            // Arrange
            Exception ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(406, response.getStatusCode().value());
            assertEquals("NOT_ACCEPTABLE", errorCodeOf(response));
        }

        @Test
        @DisplayName("A path with no handler answers 404 RESOURCE_NOT_FOUND")
        void unknownPathIs404() {
            // Arrange
            Exception ex = new NoHandlerFoundException("GET", "/api/v1/nowhere", new HttpHeaders());

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(404, response.getStatusCode().value());
            assertEquals("RESOURCE_NOT_FOUND", errorCodeOf(response));
        }

        @Test
        @DisplayName("Any other framework 4xx, such as a missing parameter, keeps its status under VALIDATION_ERROR")
        void otherClientErrorIsAValidationError() {
            // Arrange
            Exception ex = new MissingServletRequestParameterException("page", "int");

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(400, response.getStatusCode().value());
            assertEquals("VALIDATION_ERROR", errorCodeOf(response));
        }

        @Test
        @DisplayName("A framework 5xx keeps its status under INTERNAL_ERROR with the generic title")
        void frameworkServerErrorIsInternal() {
            // Arrange
            Exception ex = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(503, response.getStatusCode().value());
            assertEquals("INTERNAL_ERROR", errorCodeOf(response));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), response.getBody().getTitle());
        }

        @Test
        @DisplayName("A non-standard status reported by the framework falls back to 500 INTERNAL_ERROR")
        void nonStandardStatusFallsBackTo500() {
            // Arrange
            Exception ex = new ErrorResponseException(HttpStatusCode.valueOf(599));

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(500, response.getStatusCode().value());
            assertEquals("INTERNAL_ERROR", errorCodeOf(response));
        }

        @Test
        @DisplayName("The detail is the generic message of the code, not the wording of the framework")
        void detailIsTheGenericMessage() {
            // Arrange
            Exception ex = new HttpRequestMethodNotSupportedException("DELETE", List.of("POST"));

            // Act
            ResponseEntity<ProblemDetail> response = handler.handleFramework(ex, request);

            // Assert
            assertEquals(ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), response.getBody().getDetail());
        }
    }

    @Test
    @DisplayName("A parameter that does not convert to its type answers 400 naming the parameter, not 500")
    void typeMismatchIs400NamingTheParameter() {
        // Arrange
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("foo", Integer.class, "page",
                null, new NumberFormatException("foo"));

        // Act
        ProblemDetail problem = handler.handleTypeMismatch(ex, request);

        // Assert
        assertEquals(400, problem.getStatus());
        assertEquals("VALIDATION_ERROR", propertiesOf(problem).get("errorCode"));
        assertEquals(Map.of("page", "has an invalid value"), propertiesOf(problem).get("details"));
    }

    // ----------------------------------------------------------------------------------------
    // handleUnexpected - security critical: nothing internal may reach the client
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Unexpected failures")
    class UnexpectedFailures {

        private static final String SECRET = "jdbc:postgresql://10.0.0.5:5432/booking?password=hunter2";

        private final RuntimeException leaky = new IllegalStateException("connection refused for " + SECRET);

        @Test
        @DisplayName("An unexpected failure answers 500 under the internal error code")
        void reportsTheInternalErrorCode() {
            // Act
            ProblemDetail problem = handler.handleUnexpected(leaky, request);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
            assertEquals("INTERNAL_ERROR", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("An unexpected failure answers with the generic internal message, not with the exception message")
        void detailIsTheGenericInternalMessage() {
            // Act
            ProblemDetail problem = handler.handleUnexpected(leaky, request);

            // Assert
            assertEquals(ErrorCode.INTERNAL_ERROR.defaultMessage(), problem.getDetail());
        }

        @Test
        @DisplayName("The connection string of the failing exception never reaches the response body")
        void doesNotLeakTheExceptionMessage() {
            // Act
            ProblemDetail problem = handler.handleUnexpected(leaky, request);

            // Assert
            String rendered = problem.getTitle() + "|" + problem.getDetail() + "|" + problem.getType()
                    + "|" + problem.getInstance() + "|" + propertiesOf(problem);
            assertFalse(rendered.contains(SECRET),
                    () -> "The internal connection string leaked into the response: " + rendered);
        }

        @Test
        @DisplayName("The class name of the failing exception never reaches the response body either")
        void doesNotLeakTheExceptionType() {
            // Act
            ProblemDetail problem = handler.handleUnexpected(leaky, request);

            // Assert
            String rendered = problem.getTitle() + "|" + problem.getDetail() + "|" + problem.getType()
                    + "|" + problem.getInstance() + "|" + propertiesOf(problem);
            assertFalse(rendered.contains("IllegalStateException"),
                    () -> "The exception type leaked into the response: " + rendered);
        }

        @Test
        @DisplayName("A failure whose cause carries the secret does not leak it either")
        void doesNotLeakTheCauseMessage() {
            // Arrange
            RuntimeException wrapped = new RuntimeException("wrapper", new IllegalStateException(SECRET));

            // Act
            ProblemDetail problem = handler.handleUnexpected(wrapped, request);

            // Assert
            String rendered = problem.getTitle() + "|" + problem.getDetail() + "|" + problem.getType()
                    + "|" + problem.getInstance() + "|" + propertiesOf(problem);
            assertFalse(rendered.contains(SECRET),
                    () -> "The secret of the cause leaked into the response: " + rendered);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Trace id taken from the MDC
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Trace correlation")
    class TraceCorrelation {

        @Test
        @DisplayName("The trace id of the current request is copied from the MDC into the body so support can correlate the log line")
        void traceIdIsCopiedFromTheMdc() {
            // Arrange
            MDC.put("traceId", TRACE_ID);
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertEquals(TRACE_ID, propertiesOf(problem).get("traceId"));
        }

        @Test
        @DisplayName("With an empty MDC the trace id is published as null instead of failing the response")
        void traceIdIsNullWhenTheMdcIsEmpty() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            assertTrue(propertiesOf(problem).containsKey("traceId"));
            assertNull(propertiesOf(problem).get("traceId"));
        }
    }

    // ----------------------------------------------------------------------------------------
    // Common envelope
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Common envelope")
    class CommonEnvelope {

        @Test
        @DisplayName("Every response carries a timestamp so the client can tell two identical failures apart")
        void publishesATimestamp() {
            // Arrange
            java.time.Instant antes = java.time.Instant.now().minusSeconds(1);
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Act
            ProblemDetail problem = handler.handleBusiness(ex, request);

            // Assert
            // assertNotNull pasaría con cualquier cadena, incluida una constante: hay que
            // comprobar que es un instante y que corresponde a esta respuesta.
            Object publicado = propertiesOf(problem).get("timestamp");
            assertNotNull(publicado);
            java.time.Instant sello = java.time.Instant.parse(publicado.toString());
            assertTrue(sello.isAfter(antes) && sello.isBefore(java.time.Instant.now().plusSeconds(1)),
                    () -> "the timestamp does not belong to this response: " + sello);
        }
    }
}
