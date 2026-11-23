package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingOffering;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Receipt;
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

    private static TimeSlot time(BookingOffering offer, Slot slot) {
        return new TimeSlot(slot.start().atZone(offer.zone()).toOffsetDateTime(),
                slot.end().atZone(offer.zone()).toOffsetDateTime());
    }
}
