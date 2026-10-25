package com.codefactory.bookingplatform.catalog.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One card of the catalog (HU-005): an active service of an active provider, in one active
 * location where it is provided. A service offered in two locations is two cards.
 */
public record CatalogEntry(UUID serviceId, String serviceName, String description, int durationMinutes,
                           BigDecimal price, int customerCapacity, UUID providerId, String providerName,
                           String category, UUID locationId, String locationName, String city, String region) {
}
