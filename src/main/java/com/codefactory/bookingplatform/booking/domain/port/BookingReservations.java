package com.codefactory.bookingplatform.booking.domain.port;

import com.codefactory.bookingplatform.booking.domain.model.BookingOffering;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingReservations {
    record Slot(Instant start, Instant end, UUID sessionId, List<UUID> resources) {}
    record Receipt(UUID id, String code, BookingStatus status) {}

    Optional<BookingOffering> offering(UUID service, UUID location);
    List<Slot> slots(BookingOffering offering, LocalDate day, Instant now);
    void lockAllocation(UUID service, UUID location, UUID client);
    int recentNoShows(UUID organization, UUID client, Instant since, Instant now);
    Receipt insert(BookingOffering offering, Slot slot, UUID client, BookingStatus status);
    Optional<UUID> lockBookingClient(UUID organization, UUID booking);
    Receipt confirm(UUID booking, UUID actor);
}
