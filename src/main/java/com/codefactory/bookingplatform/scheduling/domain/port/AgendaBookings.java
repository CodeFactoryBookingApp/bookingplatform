package com.codefactory.bookingplatform.scheduling.domain.port;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;

import java.time.Instant;
import java.util.List;

/**
 * The bookings an agenda is responsible for, read straight from the bookings tables until there is
 * a booking module to ask (as ResourceBookings in the catalog).
 */
public interface AgendaBookings {

    /**
     * RESERVED or CONFIRMED bookings that end after {@code now}, in the agenda location, of one of
     * its services and, for a resource agenda, using that resource.
     */
    List<BookedSlot> future(Agenda agenda, Instant now);
}
