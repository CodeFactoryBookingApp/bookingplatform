package com.codefactory.bookingplatform.notification.domain.model;

import java.util.Objects;

/**
 * A plain-text email ready to be handed to a sender. Pure domain: it knows nothing about SMTP.
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        requireText(to, "to");
        requireText(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An email needs a non-blank " + field);
        }
    }
}
