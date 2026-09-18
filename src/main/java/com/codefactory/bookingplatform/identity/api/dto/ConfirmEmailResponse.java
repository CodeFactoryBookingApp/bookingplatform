package com.codefactory.bookingplatform.identity.api.dto;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;

import java.util.UUID;

public record ConfirmEmailResponse(UUID clientId, String email, ClientStatus status, String message) {

    public static ConfirmEmailResponse from(UUID clientId, String email, ClientStatus status) {
        return new ConfirmEmailResponse(clientId, email, status,
                "Email confirmed. The client is now ACTIVE and can confirm bookings.");
    }
}
