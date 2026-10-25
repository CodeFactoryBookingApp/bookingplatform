package com.codefactory.bookingplatform.organization.domain.model;

import java.util.UUID;

/** City from the reference catalog; {@code region} is the department. */
public record City(UUID id, String name, String region) {
}
