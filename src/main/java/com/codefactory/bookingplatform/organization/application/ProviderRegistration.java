package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;

import java.util.UUID;

/** What a successful HU-002 registration produced: the provider and the account of its owner. */
public record ProviderRegistration(UUID organizationId, UUID ownerUserId, String email, OrganizationStatus status) {
}
