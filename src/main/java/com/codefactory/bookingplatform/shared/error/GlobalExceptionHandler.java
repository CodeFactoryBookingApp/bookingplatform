package com.codefactory.bookingplatform.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    private ProblemDetail build(ErrorCode code, String message, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), message);
        problem.setTitle(code.status().is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase() : code.status().getReasonPhrase());
        problem.setType(URI.create("https://bookingplatform.codefactory.com/errors/" + code.name().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty(TRACE_ID_PROPERTY, MDC.get("traceId"));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
