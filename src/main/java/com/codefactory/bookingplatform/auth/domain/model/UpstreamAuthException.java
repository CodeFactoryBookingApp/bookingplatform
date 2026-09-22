package com.codefactory.bookingplatform.auth.domain.model;

public class UpstreamAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UpstreamAuthError error;

    public UpstreamAuthException(UpstreamAuthError error, String message) {
        super(message);
        this.error = error;
    }

    public UpstreamAuthException(UpstreamAuthError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public UpstreamAuthError error() {
        return error;
    }
}
