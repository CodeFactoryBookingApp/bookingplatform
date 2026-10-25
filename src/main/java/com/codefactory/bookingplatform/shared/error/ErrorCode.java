package com.codefactory.bookingplatform.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MINOR_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Self-registration is only allowed for adults (18+)"),
    PASSWORD_TOO_WEAK(HttpStatus.BAD_REQUEST, "Password does not meet the security policy"),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Verification token is invalid or already used"),

    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Access token is invalid"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this resource"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "None of the requested response media types is supported"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request content type is not supported"),

    EMAIL_NOT_CONFIRMED(HttpStatus.FORBIDDEN, "Account email is not confirmed yet"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Insufficient permissions"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email is already registered"),
    DUPLICATE_DOCUMENT(HttpStatus.CONFLICT, "Document is already registered"),
    DUPLICATE_IDENTIFICATION(HttpStatus.CONFLICT, "A provider with this identification is already registered"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "The resource is not in a state that allows this operation"),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "The name is already in use"),
    OVERLAPPING_PERIOD(HttpStatus.CONFLICT, "The period overlaps an existing one"),
    HAS_FUTURE_BOOKINGS(HttpStatus.CONFLICT, "There are future bookings that must be resolved first"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Account temporarily locked due to repeated failed logins"),

    UPSTREAM_AUTH_ERROR(HttpStatus.BAD_GATEWAY, "Identity provider is not available or returned an unexpected error"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
