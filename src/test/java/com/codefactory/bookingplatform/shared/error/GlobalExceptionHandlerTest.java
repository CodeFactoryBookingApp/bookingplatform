package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The global advice is the only place that turns an exception into the RFC 7807 body the client
 * sees, so every handler is invoked directly (no Spring context) and the whole ProblemDetail is
 * checked: status, title, type, instance, errorCode, traceId and the optional details.
 *
 * <p>Black box: one partition per {@code @ExceptionHandler}, plus the traced / untraced partition
 * of the MDC. White box: both branches of {@code details().isEmpty()} in {@code handleBusiness},
 * the {@code instanceof} chain and the {@code getCodes()} guard in {@code handleValidation}, and
 * both outcomes of {@code is5xxServerError()} in the private {@code build} method.</p>
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
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL), request);
            assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        }

        @Test
        @DisplayName("A business rejection publishes the machine-readable error code so the client can branch on it")
        void publishesTheErrorCode() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL), request);
            assertEquals("DUPLICATE_EMAIL", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("A business rejection points to the documentation page of its error code")
        void publishesTheTypeUri() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL), request);
            assertEquals(URI.create(TYPE_PREFIX + "duplicate_email"), problem.getType());
        }

        @ParameterizedTest(name = "{0} keeps its type URI under the Turkish locale")
        @EnumSource(ErrorCode.class)
        @DisplayName("The type URI does not depend on the JVM default locale (Turkish dotless-i trap)")
        void typeUriIsLocaleIndependent(ErrorCode code) {
            java.util.Locale previous = java.util.Locale.getDefault();
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            try {
                ProblemDetail problem = handler.handleBusiness(new BusinessException(code), request);

                assertEquals(URI.create(TYPE_PREFIX + code.name().toLowerCase(java.util.Locale.ROOT)),
                        problem.getType());
            } finally {
                java.util.Locale.setDefault(previous);
            }
        }

        @Test
        @DisplayName("A business rejection points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL), request);
            assertEquals(URI.create(REQUEST_URI), problem.getInstance());
        }

        @Test
        @DisplayName("A business rejection carries the message of the exception as the human-readable detail")
        void detailIsTheExceptionMessage() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL, "ana@example.com is already registered"), request);
            assertEquals("ana@example.com is already registered", problem.getDetail());
        }

        @Test
        @DisplayName("A 4xx business rejection uses the reason phrase of its own status as the title")
        void titleIsTheReasonPhraseForClientErrors() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.DUPLICATE_EMAIL), request);
            assertEquals(HttpStatus.CONFLICT.getReasonPhrase(), problem.getTitle());
        }

        @Test
        @DisplayName("A 5xx business rejection is titled as an internal error so the upstream failure is not disclosed")
        void titleIsMaskedForServerErrors() {
            ProblemDetail problem = handler.handleBusiness(
                    new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR), request);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), problem.getTitle());
        }

        @Test
        @DisplayName("The details property is published when the business rejection carries details")
        void detailsArePublishedWhenPresent() {
            BusinessException ex = BusinessException.of(
                    ErrorCode.DUPLICATE_DOCUMENT, "already used", "documentNumber", "1017245896");
            ProblemDetail problem = handler.handleBusiness(ex, request);
            assertEquals(Map.of("documentNumber", "1017245896"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("The details property is omitted when the business rejection carries no details, so the body stays clean")
        void detailsAreOmittedWhenEmpty() {
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.ACCESS_DENIED), request);
            assertFalse(propertiesOf(problem).containsKey("details"),
                    () -> "Expected no details property, got " + propertiesOf(problem));
        }

        @ParameterizedTest(name = "{0} keeps its status and its name in the body")
        @EnumSource(ErrorCode.class)
        @DisplayName("Every error code in the catalogue is translated into its own status and name")
        void everyErrorCodeIsTranslated(ErrorCode code) {
            ProblemDetail problem = handler.handleBusiness(new BusinessException(code), request);
            assertEquals(code.status().value(), problem.getStatus());
            assertEquals(code.name(), propertiesOf(problem).get("errorCode"));
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
            ProblemDetail problem = handler.handleValidation(
                    methodArgumentNotValid(List.of(new FieldError("payload", "email", "must not be blank"))), request);
            assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
            assertEquals("VALIDATION_ERROR", propertiesOf(problem).get("errorCode"));
        }

        @Test
        @DisplayName("A body validation failure lists each rejected field with its message")
        void fieldErrorsAreListedByFieldName() throws Exception {
            MethodArgumentNotValidException ex = methodArgumentNotValid(List.of(
                    new FieldError("payload", "email", "must not be blank"),
                    new FieldError("payload", "password", "size must be between 8 and 72")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("email", "must not be blank", "password", "size must be between 8 and 72"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A body validation failure also lists cross-field errors under the name of the validated object")
        void globalErrorsAreListedByObjectName() throws Exception {
            MethodArgumentNotValidException ex = methodArgumentNotValid(List.of(
                    new ObjectError("payload", "password and confirmation do not match")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("payload", "password and confirmation do not match"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("Field errors and cross-field errors are reported together in the same details map")
        void fieldAndGlobalErrorsAreReportedTogether() throws Exception {
            MethodArgumentNotValidException ex = methodArgumentNotValid(List.of(
                    new FieldError("payload", "email", "must not be blank"),
                    new ObjectError("payload", "password and confirmation do not match")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("email", "must not be blank",
                            "payload", "password and confirmation do not match"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure keys each error by its first resolvable code")
        void parameterErrorsAreKeyedByTheirFirstCode() {
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[]{"Size.name"}, null, "size must be between 1 and 60")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("Size.name", "size must be between 1 and 60"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure without codes falls back to the positional argument name")
        void parameterErrorsWithoutCodesFallBackToThePosition() {
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable((String[]) null, null, "must not be null")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("arg0", "must not be null"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure with an empty code array also falls back to the positional name")
        void parameterErrorsWithEmptyCodesFallBackToThePosition() {
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[0], null, "must not be null")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("arg0", "must not be null"), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("The positional fallback advances with each error, so two unnamed errors do not overwrite each other")
        void positionalFallbackAdvancesPerError() {
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable((String[]) null, null, "must not be null"),
                    new DefaultMessageSourceResolvable((String[]) null, null, "must be positive")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("arg0", "must not be null", "arg1", "must be positive"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A parameter validation failure mixing named and unnamed errors keeps the index aligned with the argument position")
        void namedAndUnnamedParameterErrorsCoexist() {
            HandlerMethodValidationException ex = handlerMethodValidation(List.of(
                    new DefaultMessageSourceResolvable(new String[]{"Min.page"}, null, "must be at least 0"),
                    new DefaultMessageSourceResolvable((String[]) null, null, "must not be null")));
            ProblemDetail problem = handler.handleValidation(ex, request);
            assertEquals(Map.of("Min.page", "must be at least 0", "arg1", "must not be null"),
                    propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A validation failure with no collected errors still answers 400 with an empty details map")
        void emptyValidationStillAnswersBadRequest() {
            ProblemDetail problem = handler.handleValidation(handlerMethodValidation(List.of()), request);
            assertEquals(Map.of(), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("An exception of neither validation type is still reported as a validation error with no details")
        void unknownValidationTypeYieldsEmptyDetails() {
            ProblemDetail problem = handler.handleValidation(new IllegalStateException("not a validation error"), request);
            assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
            assertEquals(Map.of(), propertiesOf(problem).get("details"));
        }

        @Test
        @DisplayName("A validation failure never echoes the raw exception message, only the generic validation detail")
        void detailIsTheGenericValidationMessage() throws Exception {
            ProblemDetail problem = handler.handleValidation(
                    methodArgumentNotValid(List.of(new FieldError("payload", "email", "must not be blank"))), request);
            assertEquals(ErrorCode.VALIDATION_ERROR.defaultMessage(), problem.getDetail());
        }

        @Test
        @DisplayName("A validation failure points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            ProblemDetail problem = handler.handleValidation(handlerMethodValidation(List.of()), request);
            assertEquals(URI.create(REQUEST_URI), problem.getInstance());
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
        @DisplayName("An unparsable body answers 400, because the client sent something the API cannot read")
        void answersBadRequest() {
            assertEquals(HttpStatus.BAD_REQUEST.value(), handler.handleUnreadable(unreadable, request).getStatus());
        }

        @Test
        @DisplayName("An unparsable body is reported under the validation error code")
        void reportsTheValidationErrorCode() {
            assertEquals("VALIDATION_ERROR",
                    propertiesOf(handler.handleUnreadable(unreadable, request)).get("errorCode"));
        }

        @Test
        @DisplayName("An unparsable body gets a fixed detail that does not disclose the parser internals")
        void detailIsAFixedMessage() {
            assertEquals("Malformed request body", handler.handleUnreadable(unreadable, request).getDetail());
        }

        @Test
        @DisplayName("An unparsable body points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            assertEquals(URI.create(REQUEST_URI), handler.handleUnreadable(unreadable, request).getInstance());
        }

        @Test
        @DisplayName("An unparsable body publishes no details property")
        void publishesNoDetails() {
            assertFalse(propertiesOf(handler.handleUnreadable(unreadable, request)).containsKey("details"));
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
        @DisplayName("A denied authorization answers 403")
        void answersForbidden() {
            assertEquals(HttpStatus.FORBIDDEN.value(), handler.handleAccessDenied(denied, request).getStatus());
        }

        @Test
        @DisplayName("A denied authorization is reported under the access denied error code")
        void reportsTheAccessDeniedErrorCode() {
            assertEquals("ACCESS_DENIED",
                    propertiesOf(handler.handleAccessDenied(denied, request)).get("errorCode"));
        }

        @Test
        @DisplayName("A denied authorization answers with the generic message, never with the resource the caller was probing")
        void detailDoesNotDiscloseTheProbedResource() {
            ProblemDetail problem = handler.handleAccessDenied(denied, request);
            assertEquals(ErrorCode.ACCESS_DENIED.defaultMessage(), problem.getDetail());
            assertFalse(String.valueOf(problem.getDetail()).contains("/admin/users"));
        }

        @Test
        @DisplayName("A denied authorization points to the access denied documentation page")
        void publishesTheTypeUri() {
            assertEquals(URI.create(TYPE_PREFIX + "access_denied"),
                    handler.handleAccessDenied(denied, request).getType());
        }

        @Test
        @DisplayName("A denied authorization points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            assertEquals(URI.create(REQUEST_URI), handler.handleAccessDenied(denied, request).getInstance());
        }
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
        @DisplayName("An unexpected failure answers 500")
        void answersInternalServerError() {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    handler.handleUnexpected(leaky, request).getStatus());
        }

        @Test
        @DisplayName("An unexpected failure is reported under the internal error code")
        void reportsTheInternalErrorCode() {
            assertEquals("INTERNAL_ERROR",
                    propertiesOf(handler.handleUnexpected(leaky, request)).get("errorCode"));
        }

        @Test
        @DisplayName("An unexpected failure is titled as an internal server error, masking the concrete exception")
        void titleIsMaskedAsInternalError() {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    handler.handleUnexpected(leaky, request).getTitle());
        }

        @Test
        @DisplayName("An unexpected failure answers with the generic internal message, not with the exception message")
        void detailIsTheGenericInternalMessage() {
            assertEquals(ErrorCode.INTERNAL_ERROR.defaultMessage(),
                    handler.handleUnexpected(leaky, request).getDetail());
        }

        @Test
        @DisplayName("The connection string of the failing exception never reaches the response body")
        void doesNotLeakTheExceptionMessage() {
            ProblemDetail problem = handler.handleUnexpected(leaky, request);
            String rendered = problem.getTitle() + "|" + problem.getDetail() + "|" + problem.getType()
                    + "|" + problem.getInstance() + "|" + propertiesOf(problem);
            assertFalse(rendered.contains(SECRET),
                    () -> "The internal connection string leaked into the response: " + rendered);
        }

        @Test
        @DisplayName("The class name of the failing exception never reaches the response body either")
        void doesNotLeakTheExceptionType() {
            ProblemDetail problem = handler.handleUnexpected(leaky, request);
            String rendered = problem.getTitle() + "|" + problem.getDetail() + "|" + problem.getType()
                    + "|" + problem.getInstance() + "|" + propertiesOf(problem);
            assertFalse(rendered.contains("IllegalStateException"),
                    () -> "The exception type leaked into the response: " + rendered);
        }

        @Test
        @DisplayName("No stack trace element reaches the response body")
        void doesNotLeakTheStackTrace() {
            ProblemDetail problem = handler.handleUnexpected(leaky, request);
            assertFalse(propertiesOf(problem).containsKey("stackTrace"));
            assertFalse(propertiesOf(problem).containsKey("exception"));
        }

        @Test
        @DisplayName("A failure whose cause carries the secret does not leak it either")
        void doesNotLeakTheCauseMessage() {
            RuntimeException wrapped = new RuntimeException("wrapper", new IllegalStateException(SECRET));
            ProblemDetail problem = handler.handleUnexpected(wrapped, request);
            assertFalse(String.valueOf(propertiesOf(problem)).contains(SECRET));
        }

        @Test
        @DisplayName("An unexpected failure with no message at all is still answered with the generic internal message")
        void handlesAnExceptionWithoutMessage() {
            assertEquals(ErrorCode.INTERNAL_ERROR.defaultMessage(),
                    handler.handleUnexpected(new RuntimeException(), request).getDetail());
        }

        @Test
        @DisplayName("An unexpected failure points the instance at the URI of the request that failed")
        void instanceIsTheRequestUri() {
            assertEquals(URI.create(REQUEST_URI), handler.handleUnexpected(leaky, request).getInstance());
        }

        @Test
        @DisplayName("An unexpected failure points to the internal error documentation page")
        void publishesTheTypeUri() {
            assertEquals(URI.create(TYPE_PREFIX + "internal_error"),
                    handler.handleUnexpected(leaky, request).getType());
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
            MDC.put("traceId", TRACE_ID);
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.ACCESS_DENIED), request);
            assertEquals(TRACE_ID, propertiesOf(problem).get("traceId"));
        }

        @Test
        @DisplayName("With an empty MDC the trace id is published as null instead of failing the response")
        void traceIdIsNullWhenTheMdcIsEmpty() {
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.ACCESS_DENIED), request);
            assertTrue(propertiesOf(problem).containsKey("traceId"));
            assertNull(propertiesOf(problem).get("traceId"));
        }

        @Test
        @DisplayName("The trace id is published under the key the handler advertises as its contract")
        void traceIdUsesTheAdvertisedPropertyName() {
            MDC.put("traceId", TRACE_ID);
            ProblemDetail problem = handler.handleUnexpected(new RuntimeException("boom"), request);
            assertEquals(TRACE_ID, propertiesOf(problem).get(GlobalExceptionHandler.TRACE_ID_PROPERTY));
        }

        @Test
        @DisplayName("An unrelated MDC entry is not mistaken for the trace id")
        void unrelatedMdcEntriesAreIgnored() {
            MDC.put("userId", "ana@example.com");
            ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("denied"), request);
            assertNull(propertiesOf(problem).get("traceId"));
            assertFalse(propertiesOf(problem).containsValue("ana@example.com"));
        }

        @Test
        @DisplayName("Every handler publishes the trace id, not only the business one")
        void everyHandlerPublishesTheTraceId() {
            MDC.put("traceId", TRACE_ID);
            assertEquals(TRACE_ID, propertiesOf(handler.handleValidation(
                    handlerMethodValidation(List.of()), request)).get("traceId"));
            assertEquals(TRACE_ID, propertiesOf(handler.handleUnreadable(
                    mock(HttpMessageNotReadableException.class), request)).get("traceId"));
            assertEquals(TRACE_ID, propertiesOf(handler.handleAccessDenied(
                    new AccessDeniedException("denied"), request)).get("traceId"));
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
            java.time.Instant antes = java.time.Instant.now().minusSeconds(1);

            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.ACCESS_DENIED), request);

            // assertNotNull pasaría con cualquier cadena, incluida una constante: hay que
            // comprobar que es un instante y que corresponde a esta respuesta.
            Object publicado = propertiesOf(problem).get("timestamp");
            assertNotNull(publicado);
            java.time.Instant sello = java.time.Instant.parse(publicado.toString());
            assertTrue(sello.isAfter(antes) && sello.isBefore(java.time.Instant.now().plusSeconds(1)),
                    () -> "the timestamp does not belong to this response: " + sello);
        }

        @Test
        @DisplayName("The instance follows the request, so a different endpoint is reported under its own URI")
        void instanceFollowsTheRequest() {
            MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/bookings/42");
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND), other);
            assertEquals(URI.create("/api/v1/bookings/42"), problem.getInstance());
        }

        @Test
        @DisplayName("The documentation type is the lowercase name of the error code, so every code has its own page")
        void typeIsDerivedFromTheErrorCodeName() {
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID), request);
            assertEquals(URI.create(TYPE_PREFIX + "verification_token_invalid"), problem.getType());
        }

        /**
         * Documents current behaviour, not desired behaviour: the advice builds the instance with
         * {@code URI.create(request.getRequestURI())} without encoding, so a path the servlet
         * container let through with a character illegal in a URI makes the advice itself blow up
         * and the client receives a bare container error page instead of a ProblemDetail.
         */
        @ParameterizedTest(name = "request URI [{0}] breaks the advice")
        @ValueSource(strings = {"/api/v1/bookings/a b", "/api/v1/bookings/{id}", "/api/v1/bookings/a|b"})
        @DisplayName("A request URI with a character illegal in a URI makes the advice itself fail instead of answering a problem detail")
        void requestUriWithIllegalCharacterBreaksTheAdvice(String rawUri) {
            MockHttpServletRequest malformed = new MockHttpServletRequest("GET", rawUri);
            assertThrows(IllegalArgumentException.class,
                    () -> handler.handleBusiness(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND), malformed));
        }

        @Test
        @DisplayName("A request URI with accented characters is reported normally, because non-ASCII characters are tolerated in a URI path")
        void requestUriWithAccentsIsReportedNormally() {
            MockHttpServletRequest accented = new MockHttpServletRequest("GET", "/api/v1/servicios/masaje-relajación");
            ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND), accented);
            assertEquals(URI.create("/api/v1/servicios/masaje-relajación"), problem.getInstance());
        }
    }
}
