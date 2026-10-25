package com.codefactory.bookingplatform.organization.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * The bookings of a provider, read straight from the bookings table until there is a booking module
 * to ask (as ResourceBookings in the catalog).
 */
public interface OrganizationBookings {

    /** RESERVED or CONFIRMED bookings of the provider that start after {@code now}. */
    int countFuture(UUID organizationId, Instant now);
}
