package com.codefactory.bookingplatform.identity.api.dto;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;

import java.util.UUID;

public record RegisterClientResponse(UUID clientId, String email, ClientStatus status, String message) {

    public static RegisterClientResponse from(UUID clientId, String email, ClientStatus status) {
        return new RegisterClientResponse(clientId, email, status,
                "Registration successful. A verification email was sent; the account stays PENDING_VERIFICATION until confirmed.");
    }
}
