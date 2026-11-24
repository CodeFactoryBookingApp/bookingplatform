package com.codefactory.bookingplatform.catalog.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * What the catalog needs to know about bookings before there is a booking module: how many are
 * still pending on a resource. Reads booking_resources directly; the booking module will replace
 * the adapter, not this port.
 *
 * <p>Choosing the resources of a booking (HU-010) is not here: {@code booking/available-slots.sql}
 * does it when the booking is created (HU-012).
 */
public interface ResourceBookings {

    /** Bookings RESERVED or CONFIRMED that use the resource and end after {@code now}. */
    long countFuture(UUID resourceId, Instant now);
}
