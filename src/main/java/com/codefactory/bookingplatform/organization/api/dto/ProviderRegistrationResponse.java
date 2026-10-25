package com.codefactory.bookingplatform.organization.api.dto;

import com.codefactory.bookingplatform.organization.application.ProviderRegistration;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;

import java.util.UUID;

public record ProviderRegistrationResponse(UUID organizationId, UUID ownerUserId, String email,
                                           OrganizationStatus status, String message) {

    public static ProviderRegistrationResponse from(ProviderRegistration registration) {
        return new ProviderRegistrationResponse(registration.organizationId(), registration.ownerUserId(),
                registration.email(), registration.status(),
                "Registration received. Verify your email to sign in; the business stays PENDING_APPROVAL, "
                        + "hidden from clients, until an administrator approves it.");
    }
}
