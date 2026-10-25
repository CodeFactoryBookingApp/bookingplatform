package com.codefactory.bookingplatform.identity.api.dto;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;

import java.util.UUID;

public record ConfirmEmailResponse(UUID clientId, String email, ClientStatus status, String message) {

    /** {@code status} is null for an account without a client profile, such as a provider (HU-002). */
    public static ConfirmEmailResponse from(UUID clientId, String email, ClientStatus status) {
        return new ConfirmEmailResponse(clientId, email, status, status == null
                ? "Email confirmed. The account can now sign in."
                : "Email confirmed. The client is now ACTIVE and can confirm bookings.");
    }
}
