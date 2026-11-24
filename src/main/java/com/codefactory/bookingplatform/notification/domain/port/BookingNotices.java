package com.codefactory.bookingplatform.notification.domain.port;

import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingNotices {

    List<Candidate> due(Instant at, int limit);

    boolean lockBooking(UUID bookingId);

    Optional<Delivery> claim(UUID id, Instant at);

    void markProcessed(UUID id, Instant at);

    void defer(UUID id, Instant at);
}
