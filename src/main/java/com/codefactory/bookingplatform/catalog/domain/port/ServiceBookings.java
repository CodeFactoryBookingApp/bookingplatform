package com.codefactory.bookingplatform.catalog.domain.port;

import java.util.UUID;

/**
 * What the catalog needs to know about the bookings of a service before there is a booking
 * module. Reads the bookings table directly; the booking module will replace the adapter.
 */
public interface ServiceBookings {

    /** Whether any booking, past or future, in any status, points at the service. */
    boolean exist(UUID serviceId);
}
