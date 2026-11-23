package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingOffering;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Receipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Slot;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookAppointmentUseCase {
    public record TimeSlot(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    public record Preview(String service, String organization, String location, TimeSlot slot,
                          java.math.BigDecimal price, String currency, int policyVersion,
                          int freeCancellationWindowMinutes, boolean manualConfirmation, String termsVersion) {}

    private final BookingReservations bookings;
    private final ClientRepository clients;
    private final OrganizationAccess access;
    private final AuditActor audit;
    private final Clock clock;

    public BookAppointmentUseCase(BookingReservations bookings, ClientRepository clients, OrganizationAccess access,
                                  AuditActor audit, Clock clock) {
        this.bookings = bookings;
        this.clients = clients;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> availability(UUID service, UUID location, LocalDate date) {
        var offer = offer(service, location);
        return bookings.slots(offer, date, clock.instant()).stream().map(slot -> time(offer, slot)).toList();
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID service, UUID location, Instant start) {
        var offer = offer(service, location);
        var slot = select(offer, start, clock.instant());
        return new Preview(offer.service(), offer.organization(), offer.location(), time(offer, slot), offer.price(),
                offer.currency(), offer.policyVersion(), offer.freeCancellationWindowMinutes(),
                offer.manualConfirmation(), offer.termsVersion(start));
    }

    @Transactional
    public Receipt create(UUID client, UUID service, UUID location, Instant start, String termsVersion) {
        bookings.lockAllocation(service, location, client);
        requireVerified(client);
        var offer = offer(service, location);
        Instant now = clock.instant();
        var slot = select(offer, start, now);
        if (!offer.termsVersion(start).equals(termsVersion)) {
            throw new BusinessException(ErrorCode.BOOKING_TERMS_CHANGED);
        }
        int noShows = bookings.recentNoShows(offer.organizationId(), client, now.minus(90, ChronoUnit.DAYS), now);
        audit.identify(client, "CLIENT");
        return bookings.insert(offer, slot, client, offer.initialStatus(noShows));
    }

    @Transactional
    public Receipt confirm(UUID organization, UUID booking, UUID actor) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER);
        UUID client = bookings.lockBookingClient(organization, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireVerified(client);
        return bookings.confirm(booking, actor);
    }

    @Transactional
    public CancellationReceipt cancelByClient(UUID client, UUID booking, boolean confirmed, String reason) {
        CancellationTarget target = bookings.lockForClientCancellation(client, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.instant();
        requireCancellable(target, now);
        boolean late = target.lateCancellation(now);
        if (!confirmed) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED,
                    late ? "Cancelling outside the free window incurs a late cancellation penalty; confirm to proceed"
                            : "Confirm the cancellation to release the slot without penalty",
                    Map.of("penaltyApplies", Boolean.toString(late), "lateCancellation", Boolean.toString(late),
                            "freeCancellationWindowMinutes", Integer.toString(target.freeCancellationWindowMinutes())));
        }
        String note = reason == null || reason.isBlank()
                ? (late ? "Cancelación tardía del cliente" : "Cancelación del cliente")
                : reason.trim();
        audit.identify(client, "CLIENT");
        return bookings.cancel(target, now, late, note, client);
    }

    @Transactional
    public CancellationReceipt cancelByProvider(UUID organization, UUID booking, UUID actor, String reason) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER);
        CancellationTarget target = bookings.lockForProviderCancellation(organization, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.instant();
        requireCancellable(target, now);
        return bookings.cancel(target, now, false, reason.trim(), actor);
    }

    @Transactional
    public void reactivateByClient(UUID client, UUID booking) {
        bookings.lockForClientCancellation(client, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                "A cancelled booking cannot be reactivated; create a new booking instead");
    }

    @Transactional
    public RescheduleReceipt rescheduleByClient(UUID client, UUID booking, Instant startsAt, UUID locationId,
                                                UUID resourceId, UUID requestedServiceId) {
        RescheduleTarget target = bookings.lockForReschedule(client, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (requestedServiceId != null && !requestedServiceId.equals(target.serviceId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The service cannot be changed when rescheduling; only date, time, location or resource can change",
                    Map.of("serviceId", "cannot be changed when rescheduling"));
        }
        Instant now = clock.instant();
        if (!target.reschedulable(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Booking cannot be rescheduled in its current state or after it has started");
        }
        if (target.limitReached()) {
            throw new BusinessException(ErrorCode.RESCHEDULE_LIMIT_REACHED,
                    "Maximum reschedule limit reached; cancel this booking and create a new one",
                    Map.of("maxReschedules", Integer.toString(target.maxReschedules()),
                            "rescheduleCount", Integer.toString(target.rescheduleCount()),
                            "suggestedAction", "CANCEL_AND_CREATE_NEW"));
        }
        UUID destinationLocation = locationId == null ? target.locationId() : locationId;
        bookings.lockAllocation(target.serviceId(), destinationLocation, client);
        var offer = bookings.offering(target.serviceId(), destinationLocation)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The service is not offered at the requested location",
                        Map.of("locationId", "service is not offered at this location")));
        Slot slot = selectForReschedule(offer, startsAt, now, target.id(), resourceId);
        if (target.unchanged(destinationLocation, slot)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Select a different date, time, location or resource to reschedule",
                    Map.of("startsAt", "must change date, time, location or resource"));
        }
        boolean late = target.lateRescheduleAt(now);
        audit.identify(client, "CLIENT");
        return bookings.reschedule(target, destinationLocation, slot, late);
    }

    @Transactional
    public AttendanceReceipt recordAttendance(UUID organization, UUID booking, UUID actor, BookingStatus status,
                                              String observation) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER, MemberRole.STAFF);
        if (status != BookingStatus.COMPLETED && status != BookingStatus.NO_SHOW) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Attendance closure status must be COMPLETED or NO_SHOW",
                    Map.of("status", "must be COMPLETED or NO_SHOW"));
        }
        AttendanceTarget target = bookings.lockForAttendance(organization, booking)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target.status() == BookingStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A cancelled booking cannot be marked as completed or no-show");
        }
        Instant now = clock.instant();
        if (!target.hasStarted(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Attendance cannot be recorded before the booking start time");
        }
        if (target.closed()) {
            if (target.correctionWindowExpired(now)) {
                throw new BusinessException(ErrorCode.CLOSURE_CORRECTION_WINDOW_EXPIRED);
            }
            if (target.status() == status) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Booking is already closed with status " + status);
            }
        }
        String note = observation == null || observation.isBlank() ? null : observation.trim();
        return bookings.closeAttendance(target, status, actor, now, note);
    }

    @Transactional
    public int autoCloseUnmarked() {
        return bookings.autoCloseOverdue(clock.instant());
    }

    private static void requireCancellable(CancellationTarget target, Instant now) {
        if (!target.cancellable(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Booking cannot be cancelled in its current state or after it has started");
        }
    }

    private void requireVerified(UUID client) {
        Client account = clients.findById(client).orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        if (!account.canConfirmBooking()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "An active verified client account is required");
        }
    }

    private BookingOffering offer(UUID service, UUID location) {
        return bookings.offering(service, location).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Slot select(BookingOffering offer, Instant start, Instant now) {
        return bookings.slots(offer, start.atZone(offer.zone()).toLocalDate(), now).stream()
                .filter(slot -> slot.start().equals(start)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_UNAVAILABLE));
    }

    private Slot selectForReschedule(BookingOffering offer, Instant start, Instant now, UUID bookingId,
                                     UUID preferredResource) {
        return bookings.slotsForReschedule(offer, start.atZone(offer.zone()).toLocalDate(), now, bookingId, preferredResource)
                .stream()
                .filter(slot -> slot.start().equals(start))
                .filter(slot -> preferredResource == null || slot.resources().contains(preferredResource))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_UNAVAILABLE));
    }

    private static TimeSlot time(BookingOffering offer, Slot slot) {
        return new TimeSlot(slot.start().atZone(offer.zone()).toOffsetDateTime(),
                slot.end().atZone(offer.zone()).toOffsetDateTime());
    }
}
