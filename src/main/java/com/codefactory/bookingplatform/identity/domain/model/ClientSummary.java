package com.codefactory.bookingplatform.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One row of the client listing of the administrator (HU-003). */
public record ClientSummary(UUID id, String fullName, String document, String email, String phone, String city,
                            NotificationChannel notificationChannel, ClientStatus status, Instant registeredAt) {
}
