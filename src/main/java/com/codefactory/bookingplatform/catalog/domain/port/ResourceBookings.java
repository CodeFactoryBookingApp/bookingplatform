package com.codefactory.bookingplatform.catalog.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * What the catalog needs to know about bookings before there is a booking module: how many are
 * still pending on a resource. Reads booking_resources directly; the booking module will replace
 * the adapter, not this port.
 */
public interface ResourceBookings {

    /** Bookings RESERVED or CONFIRMED that use the resource and end after {@code now}. */
    long countFuture(UUID resourceId, Instant now);

    /** Among the given resources, which already have a RESERVED/CONFIRMED booking overlapping [from, to) (HU-010). */
    Set<UUID> busyBetween(Collection<UUID> resourceIds, Instant from, Instant to);
}