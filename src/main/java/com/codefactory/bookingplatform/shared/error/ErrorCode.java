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

    EMAIL_NOT_CONFIRMED(HttpStatus.FORBIDDEN, "Account email is not confirmed yet"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Insufficient permissions"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email is already registered"),
    DUPLICATE_DOCUMENT(HttpStatus.CONFLICT, "Document is already registered"),

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
