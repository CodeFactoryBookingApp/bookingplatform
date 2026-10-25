package com.codefactory.bookingplatform.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One row of the provider listing of the administrator (HU-003). {@code cities} lists the cities of its locations. */
public record ProviderSummary(UUID id, String name, IdentificationType identificationType, String identificationNumber,
                              String category, String contactEmail, String contactPhone, String cities,
                              OrganizationStatus status, Instant registeredAt, Instant approvedAt) {
}
