package com.codefactory.bookingplatform.organization.domain.model;

import java.util.UUID;

/** Business category from the reference catalog (HU-002, HU-003, HU-005). */
public record Category(UUID id, String name) {
}
