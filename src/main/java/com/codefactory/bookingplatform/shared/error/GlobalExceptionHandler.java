package com.codefactory.bookingplatform.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public static final String TRACE_ID_PROPERTY = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        log.warn("Business rule rejected request [{}]: {}", code, ex.getMessage());
        ProblemDetail problem = build(code, ex.getMessage(), request);
        if (!ex.details().isEmpty()) {
            problem.setProperty("details", ex.details());
        }
        return problem;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ProblemDetail handleValidation(Exception ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        if (ex instanceof MethodArgumentNotValidException manve) {
            for (FieldError fieldError : manve.getBindingResult().getFieldErrors()) {
                details.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            manve.getBindingResult().getGlobalErrors()
                    .forEach(e -> details.put(e.getObjectName(), e.getDefaultMessage()));
        } else if (ex instanceof HandlerMethodValidationException hmve) {
            int index = 0;
            for (org.springframework.context.MessageSourceResolvable error : hmve.getAllErrors()) {
                String key = (error.getCodes() != null && error.getCodes().length > 0)
                        ? error.getCodes()[0]
                        : "arg" + index;
                details.put(key, error.getDefaultMessage());
                index++;
            }
        }
        ProblemDetail problem = build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request);
        problem.setProperty("details", details);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problem = build(ErrorCode.VALIDATION_ERROR, "Malformed request body", request);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), request);
    }

    /**
     * Spring MVC's own client errors: an unsupported method or media type, an unknown path, a
     * missing parameter. Each one already carries its status and headers (Allow on a 405, Accept
     * on a 415); without this handler they fell into {@link #handleUnexpected} as 500 (D9).
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class,
            NoResourceFoundException.class,
            NoHandlerFoundException.class,
            ServletRequestBindingException.class,
            ErrorResponseException.class
    })
    public ResponseEntity<ProblemDetail> handleFramework(Exception ex, HttpServletRequest request) {
        ErrorResponse response = (ErrorResponse) ex;
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        ErrorCode code = codeFor(status);
        if (status == null) {
            status = code.status();
        }
        if (status.is5xxServerError()) {
            log.error("Framework failure processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.warn("Request rejected by Spring MVC [{}]: {} {}", status.value(), request.getMethod(),
                    request.getRequestURI());
        }
        return ResponseEntity.status(status)
                .headers(response.getHeaders())
                .body(build(status, code, code.defaultMessage(), request));
    }

    private static ErrorCode codeFor(HttpStatus status) {
        if (status == null) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status) {
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCode.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    private ProblemDetail build(ErrorCode code, String message, HttpServletRequest request) {
        return build(code.status(), code, message, request);
    }

    private ProblemDetail build(HttpStatus status, ErrorCode code, String message, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase() : status.getReasonPhrase());
        // Locale.ROOT: the type URI is a stable identifier, not display text.
        // Under a Turkish default locale VALIDATION_ERROR would lower case to
        // "valıdatıon_error" and the published error type would stop matching.
        problem.setType(URI.create("https://bookingplatform.codefactory.com/errors/" + code.name().toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty(TRACE_ID_PROPERTY, MDC.get("traceId"));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
