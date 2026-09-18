package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;

import java.util.UUID;

public record RegistrationOutcome(UUID clientId, String email, ClientStatus status) {
}
