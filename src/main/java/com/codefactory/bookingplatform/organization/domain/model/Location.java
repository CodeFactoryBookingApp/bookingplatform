package com.codefactory.bookingplatform.organization.domain.model;

import java.util.UUID;

/**
 * A branch of the provider (HU-002). {@code timezone} is null when the branch uses the
 * organization's.
 */
public record Location(UUID id, String name, String address, UUID cityId, String timezone) {
}
