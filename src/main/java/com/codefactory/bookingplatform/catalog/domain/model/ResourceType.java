package com.codefactory.bookingplatform.catalog.domain.model;

import java.util.UUID;

/** A kind of resource a provider defines: chair, room, court, professional (HU-009). */
public record ResourceType(UUID id, UUID organizationId, String name, String description) {
}
