package com.codefactory.bookingplatform.auth.domain.model;

public enum UpstreamAuthError {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    USER_ALREADY_EXISTS,
    USER_NOT_FOUND,
    TOKEN_INVALID,
    TOKEN_EXPIRED,
    RATE_LIMITED,
    UNAVAILABLE
}
