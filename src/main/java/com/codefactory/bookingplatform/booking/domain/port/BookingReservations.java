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
    record CancellationTarget(UUID id, UUID clientId, UUID organizationId, BookingStatus status, Instant startsAt,
                              int freeCancellationWindowMinutes) {
        public boolean cancellable(Instant now) {
            return (status == BookingStatus.RESERVED || status == BookingStatus.CONFIRMED) && startsAt.isAfter(now);
        }

        public boolean lateCancellation(Instant now) {
            return now.plusSeconds(freeCancellationWindowMinutes * 60L).isAfter(startsAt);
        }
    }
    record CancellationReceipt(UUID id, String code, BookingStatus status, boolean lateCancellation) {}
    record RescheduleTarget(UUID id, String code, UUID clientId, UUID organizationId, UUID serviceId, UUID locationId,
                            UUID sessionId, BookingStatus status, Instant startsAt, Instant endsAt,
                            int freeCancellationWindowMinutes, int maxReschedules, int rescheduleCount,
                            boolean lateReschedule, List<UUID> resourceIds) {
        public boolean reschedulable(Instant now) {
            return (status == BookingStatus.RESERVED || status == BookingStatus.CONFIRMED) && startsAt.isAfter(now);
        }

        public boolean limitReached() {
            return rescheduleCount >= maxReschedules;
        }

        public boolean lateRescheduleAt(Instant now) {
            return now.plusSeconds(freeCancellationWindowMinutes * 60L).isAfter(startsAt);
        }

        public boolean unchanged(UUID targetLocation, Slot slot) {
            return locationId.equals(targetLocation) && startsAt.equals(slot.start()) && endsAt.equals(slot.end())
                    && resourceIds.equals(slot.resources());
        }
    }
    record RescheduleReceipt(UUID id, String code, BookingStatus status, UUID locationId, Instant startsAt,
                             Instant endsAt, int rescheduleCount, boolean lateReschedule) {}

    Optional<BookingOffering> offering(UUID service, UUID location);
    List<Slot> slots(BookingOffering offering, LocalDate day, Instant now);
    List<Slot> slotsForReschedule(BookingOffering offering, LocalDate day, Instant now, UUID ignoredBooking,
                                  UUID preferredResource);
    void lockAllocation(UUID service, UUID location, UUID client);
    int recentNoShows(UUID organization, UUID client, Instant since, Instant now);
    Receipt insert(BookingOffering offering, Slot slot, UUID client, BookingStatus status);
    Optional<UUID> lockBookingClient(UUID organization, UUID booking);
    Receipt confirm(UUID booking, UUID actor);
    Optional<CancellationTarget> lockForClientCancellation(UUID client, UUID booking);
    Optional<CancellationTarget> lockForProviderCancellation(UUID organization, UUID booking);
    CancellationReceipt cancel(CancellationTarget target, Instant cancelledAt, boolean lateCancellation, String reason,
                               UUID actor);
    Optional<RescheduleTarget> lockForReschedule(UUID client, UUID booking);
    RescheduleReceipt reschedule(RescheduleTarget target, UUID targetLocation, Slot slot, boolean lateReschedule);
}
