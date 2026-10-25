package com.codefactory.bookingplatform.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/** An email the platform kept in its inbox instead of sending it over SMTP (ADR-0007). */
public record SentEmail(UUID id, String to, String subject, String body, Instant sentAt) {
}
