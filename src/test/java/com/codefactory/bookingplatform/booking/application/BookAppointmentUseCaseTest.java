package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.BookingActions;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.ClientBookingItem;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.ClientBookingsView;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.Preview;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.RebookView;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.TimeSlot;
import com.codefactory.bookingplatform.booking.domain.model.BookingOffering;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingDetail;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingRecord;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Receipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Slot;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reservation flow of the client and of the provider: create (HU-012), cancel and reactivate (HU-013),
 * reschedule (HU-014), attendance and no-show (HU-016) and the client's history (HU-017).
 *
 * <p>Techniques: state transition (which status and moment allow each operation), equivalence
 * partitioning over the caller's role (OWNER, MANAGER, STAFF, outsider) and over the client account
 * (active, pending, suspended, unknown), decision tables over the free window x confirmation x reason,
 * and boundary values on time zones (a UTC instant whose local date in the provider's zone differs).
 * The port is mocked: these tests verify the decisions of the use case, what it asks the port for
 * and what it refuses before touching it.
 */
class BookAppointmentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID RESOURCE = UUID.randomUUID();
    private static final int WINDOW_MINUTES = 60;

    private BookingReservations bookings;
    private ClientRepository clients;
    private OrganizationRepository organizations;
    private AuditActor audit;
    private BookAppointmentUseCase useCase;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingReservations.class);
        clients = mock(ClientRepository.class);
        organizations = mock(OrganizationRepository.class);
        audit = mock(AuditActor.class);
        useCase = new BookAppointmentUseCase(bookings, clients, new OrganizationAccess(organizations, audit), audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BookingOffering offering(boolean manual) {
        return new BookingOffering(SERVICE, "Corte", LOCATION, "Sede", ORG, "Barbería", BOGOTA,
                new BigDecimal("45000.00"), "COP", 30, 0, 0, 1, UUID.randomUUID(), 3, WINDOW_MINUTES, 600, 43200,
                manual);
    }

    private static Slot slot(Instant start) {
        return new Slot(start, start.plusSeconds(1800), UUID.randomUUID(), List.of(RESOURCE));
    }

    private void member(MemberRole role) {
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.of(role));
    }

    private void account(ClientStatus status) {
        when(clients.findById(CLIENT)).thenReturn(Optional.of(new Client(CLIENT, "Ana", "123", LocalDate.of(1990, 1, 1),
                "ana@example.com", "300", "Medellín", null, status)));
    }

    private static BusinessException rejected(org.junit.jupiter.api.function.Executable act) {
        return assertThrows(BusinessException.class, act);
    }

    @Nested
    @DisplayName("HU-012 create")
    class Create {

        /** 2026-11-27T03:00Z is still 2026-11-26 in Bogota. */
        private final Instant lateEvening = Instant.parse("2026-11-27T03:00:00Z");
        private final Slot free = slot(lateEvening);
        private BookingOffering offer;

        @BeforeEach
        void offer() {
            offer = offering(false);
            when(bookings.offering(SERVICE, LOCATION)).thenReturn(Optional.of(offer));
            when(bookings.slots(offer, LocalDate.of(2026, 11, 26), NOW)).thenReturn(List.of(free));
        }

        @ParameterizedTest(name = "client {0}")
        @EnumSource(value = ClientStatus.class, names = {"PENDING_VERIFICATION", "SUSPENDED"})
        @DisplayName("HU-001 / HU-012: only an active verified client can book; nothing is inserted otherwise")
        void unverifiedClientCannotBook(ClientStatus status) {
            // Arrange
            account(status);
            String terms = offer.termsVersion(lateEvening);

            // Act
            BusinessException ex = rejected(() -> useCase.create(CLIENT, SERVICE, LOCATION, lateEvening, terms));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(bookings, never()).insert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-012: a token without a client account is denied, not booked")
        void unknownClientCannotBook() {
            // Arrange
            when(clients.findById(CLIENT)).thenReturn(Optional.empty());
            String terms = offer.termsVersion(lateEvening);

            // Act
            BusinessException ex = rejected(() -> useCase.create(CLIENT, SERVICE, LOCATION, lateEvening, terms));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(bookings, never()).insert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-012: terms different from the ones reviewed in the preview are rejected with BOOKING_TERMS_CHANGED")
        void staleTermsAreRejected() {
            // Arrange
            account(ClientStatus.ACTIVE);
            String stale = offer.termsVersion(lateEvening.plusSeconds(1800));

            // Act
            BusinessException ex = rejected(() -> useCase.create(CLIENT, SERVICE, LOCATION, lateEvening, stale));

            // Assert
            assertEquals(ErrorCode.BOOKING_TERMS_CHANGED, ex.errorCode());
            verify(bookings, never()).insert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-012: a start that is not among the free slots of its local day is SLOT_UNAVAILABLE")
        void startOutsideTheFreeSlotsIsUnavailable() {
            // Arrange
            account(ClientStatus.ACTIVE);
            Instant taken = lateEvening.plusSeconds(1800);
            String terms = offer.termsVersion(taken);

            // Act
            BusinessException ex = rejected(() -> useCase.create(CLIENT, SERVICE, LOCATION, taken, terms));

            // Assert
            assertEquals(ErrorCode.SLOT_UNAVAILABLE, ex.errorCode());
            verify(bookings, never()).insert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-012: an offer that does not exist at that location is RESOURCE_NOT_FOUND")
        void serviceNotOfferedAtLocation() {
            // Arrange
            account(ClientStatus.ACTIVE);
            UUID otherLocation = UUID.randomUUID();
            when(bookings.offering(SERVICE, otherLocation)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.create(CLIENT, SERVICE, otherLocation, lateEvening, "x"));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @ParameterizedTest(name = "{0} recent no-shows -> {1}")
        @CsvSource({"3, CONFIRMED", "4, RESERVED"})
        @DisplayName("HU-012: the slot of the provider's local day is booked, confirmed unless the client has more than 3 no-shows in the last 90 days at that provider")
        void insertsTheSelectedSlotWithTheStatusFromRecentNoShows(int noShows, BookingStatus expected) {
            // Arrange
            account(ClientStatus.ACTIVE);
            when(bookings.recentNoShows(ORG, CLIENT, Instant.parse("2026-08-26T15:00:00Z"), NOW)).thenReturn(noShows);
            Receipt receipt = new Receipt(BOOKING, "RES-1", expected);
            when(bookings.insert(any(), any(), any(), any())).thenReturn(receipt);

            // Act
            Receipt created = useCase.create(CLIENT, SERVICE, LOCATION, lateEvening, offer.termsVersion(lateEvening));

            // Assert
            assertSame(receipt, created);
            verify(bookings).lockAllocation(SERVICE, LOCATION, CLIENT);
            verify(bookings).insert(offer, free, CLIENT, expected);
            verify(audit).identify(CLIENT, "CLIENT");
        }
    }

    @Nested
    @DisplayName("HU-012 provider confirmation")
    class Confirm {

        @Test
        @DisplayName("ADR-0008: STAFF cannot approve a pending booking")
        void staffCannotConfirm() {
            // Arrange
            member(MemberRole.STAFF);

            // Act
            BusinessException ex = rejected(() -> useCase.confirm(ORG, BOOKING, ACTOR));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(bookings, never()).confirm(any(), any());
        }

        @Test
        @DisplayName("HU-012: a booking of another provider is not found, not confirmed")
        void bookingOutsideTheProviderIsNotFound() {
            // Arrange
            member(MemberRole.OWNER);
            when(bookings.lockBookingClient(ORG, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.confirm(ORG, BOOKING, ACTOR));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verify(bookings, never()).confirm(any(), any());
        }

        @Test
        @DisplayName("HU-001: a booking of a client who is no longer active cannot be confirmed")
        void suspendedClientBookingIsNotConfirmed() {
            // Arrange
            member(MemberRole.MANAGER);
            when(bookings.lockBookingClient(ORG, BOOKING)).thenReturn(Optional.of(CLIENT));
            account(ClientStatus.SUSPENDED);

            // Act
            BusinessException ex = rejected(() -> useCase.confirm(ORG, BOOKING, ACTOR));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(bookings, never()).confirm(any(), any());
        }

        @Test
        @DisplayName("HU-012: a manager confirms the pending booking of an active client, recorded as the actor")
        void managerConfirms() {
            // Arrange
            member(MemberRole.MANAGER);
            when(bookings.lockBookingClient(ORG, BOOKING)).thenReturn(Optional.of(CLIENT));
            account(ClientStatus.ACTIVE);
            Receipt receipt = new Receipt(BOOKING, "RES-1", BookingStatus.CONFIRMED);
            when(bookings.confirm(BOOKING, ACTOR)).thenReturn(receipt);

            // Act
            Receipt confirmed = useCase.confirm(ORG, BOOKING, ACTOR);

            // Assert
            assertSame(receipt, confirmed);
        }
    }

    @Nested
    @DisplayName("HU-013 cancellation by the client")
    class CancelByClient {

        private CancellationTarget target(BookingStatus status, long secondsToStart) {
            return new CancellationTarget(BOOKING, CLIENT, ORG, status, NOW.plusSeconds(secondsToStart), WINDOW_MINUTES);
        }

        private void locked(CancellationTarget target) {
            when(bookings.lockForClientCancellation(CLIENT, BOOKING)).thenReturn(Optional.of(target));
        }

        @Test
        @DisplayName("HU-013: another client's booking is not found")
        void otherClientsBookingIsNotFound() {
            // Arrange
            when(bookings.lockForClientCancellation(CLIENT, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByClient(CLIENT, BOOKING, true, null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @ParameterizedTest(name = "{0}, starts in {1} s")
        @CsvSource({"CONFIRMED, 0", "CONFIRMED, -600", "CANCELLED, 86400", "COMPLETED, -7200", "NO_SHOW, -7200"})
        @DisplayName("HU-013: a booking already started, cancelled or closed cannot be cancelled (409), confirmed or not")
        void nonCancellableIsAConflict(BookingStatus status, long secondsToStart) {
            // Arrange
            locked(target(status, secondsToStart));

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByClient(CLIENT, BOOKING, false, null));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).cancel(any(), any(), anyBoolean(), any(), any());
        }

        @ParameterizedTest(name = "starts in {0} s -> penalty {1}")
        @CsvSource({"3600, false", "3599, true"})
        @DisplayName("HU-013: without confirmation nothing is cancelled and the client is told whether the penalty applies")
        void unconfirmedCancellationAsksForConfirmation(long secondsToStart, String penalty) {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, secondsToStart));

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByClient(CLIENT, BOOKING, false, "Viaje"));

            // Assert
            assertEquals(ErrorCode.CONFIRMATION_REQUIRED, ex.errorCode());
            assertEquals(Boolean.parseBoolean(penalty)
                    ? "Cancelling outside the free window incurs a late cancellation penalty; confirm to proceed"
                    : "Confirm the cancellation to release the slot without penalty", ex.getMessage());
            assertEquals(Map.of("penaltyApplies", penalty, "lateCancellation", penalty,
                    "freeCancellationWindowMinutes", "60"), ex.details());
            verify(bookings, never()).cancel(any(), any(), anyBoolean(), any(), any());
        }

        @ParameterizedTest(name = "starts in {0} s, reason [{1}] -> late {2}, note [{3}]")
        @CsvSource(value = {
                "3600 | NULL | false | Cancelación del cliente",
                "3599 | NULL | true | Cancelación tardía del cliente",
                "7200 | '   ' | false | Cancelación del cliente",
                "60 | '  Viaje imprevisto  ' | true | Viaje imprevisto"
        }, delimiter = '|', nullValues = "NULL")
        @DisplayName("HU-013: a confirmed cancellation is recorded as late or not, with the client's reason or a default one")
        void confirmedCancellationRecordsLatenessAndReason(long secondsToStart, String reason, boolean late, String note) {
            // Arrange
            CancellationTarget target = target(BookingStatus.RESERVED, secondsToStart);
            locked(target);
            CancellationReceipt receipt = new CancellationReceipt(BOOKING, "RES-1", BookingStatus.CANCELLED, late);
            when(bookings.cancel(target, NOW, late, note, CLIENT)).thenReturn(receipt);

            // Act
            CancellationReceipt cancelled = useCase.cancelByClient(CLIENT, BOOKING, true, reason);

            // Assert
            assertSame(receipt, cancelled);
            verify(audit).identify(CLIENT, "CLIENT");
        }
    }

    @Nested
    @DisplayName("HU-013 cancellation by the provider")
    class CancelByProvider {

        @Test
        @DisplayName("ADR-0008: STAFF cannot cancel a booking on behalf of the provider")
        void staffCannotCancel() {
            // Arrange
            member(MemberRole.STAFF);

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByProvider(ORG, BOOKING, ACTOR, "Falla"));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(bookings, never()).lockForProviderCancellation(any(), any());
        }

        @Test
        @DisplayName("HU-013: a booking outside the provider is not found")
        void bookingOfAnotherProviderIsNotFound() {
            // Arrange
            member(MemberRole.OWNER);
            when(bookings.lockForProviderCancellation(ORG, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByProvider(ORG, BOOKING, ACTOR, "Falla"));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @Test
        @DisplayName("HU-013: the provider cannot cancel a booking that already started")
        void startedBookingIsAConflict() {
            // Arrange
            member(MemberRole.OWNER);
            when(bookings.lockForProviderCancellation(ORG, BOOKING)).thenReturn(Optional.of(
                    new CancellationTarget(BOOKING, CLIENT, ORG, BookingStatus.CONFIRMED, NOW, WINDOW_MINUTES)));

            // Act
            BusinessException ex = rejected(() -> useCase.cancelByProvider(ORG, BOOKING, ACTOR, "Falla"));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).cancel(any(), any(), anyBoolean(), any(), any());
        }

        @Test
        @DisplayName("HU-013: a provider cancellation inside the client's window carries no penalty and keeps the trimmed reason")
        void providerCancellationIsNeverLate() {
            // Arrange
            member(MemberRole.MANAGER);
            CancellationTarget target = new CancellationTarget(BOOKING, CLIENT, ORG, BookingStatus.CONFIRMED,
                    NOW.plusSeconds(60), WINDOW_MINUTES);
            when(bookings.lockForProviderCancellation(ORG, BOOKING)).thenReturn(Optional.of(target));
            CancellationReceipt receipt = new CancellationReceipt(BOOKING, "RES-1", BookingStatus.CANCELLED, false);
            when(bookings.cancel(target, NOW, false, "Mantenimiento de emergencia", ACTOR)).thenReturn(receipt);

            // Act
            CancellationReceipt cancelled = useCase.cancelByProvider(ORG, BOOKING, ACTOR, "  Mantenimiento de emergencia  ");

            // Assert
            assertSame(receipt, cancelled);
        }
    }

    @Nested
    @DisplayName("HU-013 reactivation")
    class Reactivate {

        @Test
        @DisplayName("HU-013: the client's own cancelled booking cannot be reactivated; a new booking is required")
        void cancelledBookingIsNotReactivated() {
            // Arrange
            when(bookings.lockForClientCancellation(CLIENT, BOOKING)).thenReturn(Optional.of(
                    new CancellationTarget(BOOKING, CLIENT, ORG, BookingStatus.CANCELLED, NOW.plusSeconds(86400), 60)));

            // Act
            BusinessException ex = rejected(() -> useCase.reactivateByClient(CLIENT, BOOKING));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }

        @Test
        @DisplayName("HU-013: reactivating someone else's booking does not reveal it exists")
        void otherClientsBookingIsNotFound() {
            // Arrange
            when(bookings.lockForClientCancellation(CLIENT, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.reactivateByClient(CLIENT, BOOKING));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("HU-014 reschedule")
    class Reschedule {

        private final Instant current = NOW.plusSeconds(2 * 86400);
        private final Instant next = current.plusSeconds(3600);
        private final UUID otherLocation = UUID.randomUUID();
        private BookingOffering offer;

        private RescheduleTarget target(BookingStatus status, Instant startsAt, int max, int count) {
            return new RescheduleTarget(BOOKING, "RES-1", CLIENT, ORG, SERVICE, LOCATION, UUID.randomUUID(), status,
                    startsAt, startsAt.plusSeconds(1800), WINDOW_MINUTES, max, count, false, List.of(RESOURCE));
        }

        private void locked(RescheduleTarget target) {
            when(bookings.lockForReschedule(CLIENT, BOOKING)).thenReturn(Optional.of(target));
        }

        @BeforeEach
        void offer() {
            offer = offering(false);
            when(bookings.offering(SERVICE, LOCATION)).thenReturn(Optional.of(offer));
        }

        private void freeSlots(UUID preferred, Slot... slots) {
            when(bookings.slotsForReschedule(offer, next.atZone(BOGOTA).toLocalDate(), NOW, BOOKING, preferred))
                    .thenReturn(List.of(slots));
        }

        @Test
        @DisplayName("HU-014: another client's booking is not found")
        void otherClientsBookingIsNotFound() {
            // Arrange
            when(bookings.lockForReschedule(CLIENT, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null, null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @Test
        @DisplayName("HU-014: the service cannot change when rescheduling (400 naming serviceId)")
        void serviceCannotChange() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 1, 0));
            UUID otherService = UUID.randomUUID();

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null,
                    otherService));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("serviceId", "cannot be changed when rescheduling"), ex.details());
            verify(bookings, never()).reschedule(any(), any(), any(), anyBoolean());
        }

        @ParameterizedTest(name = "{0}, starts in {1} s")
        @CsvSource({"CANCELLED, 172800", "COMPLETED, -7200", "NO_SHOW, -7200", "CONFIRMED, 0"})
        @DisplayName("HU-014: a cancelled, closed or started booking cannot be rescheduled (409)")
        void notReschedulableIsAConflict(BookingStatus status, long secondsToStart) {
            // Arrange
            locked(target(status, NOW.plusSeconds(secondsToStart), 1, 0));

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null, null));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).reschedule(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("HU-014: at the reschedule limit the client is told the limit and to cancel and book again")
        void limitReachedSuggestsCancelAndCreate() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 2, 2));

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null, null));

            // Assert
            assertEquals(ErrorCode.RESCHEDULE_LIMIT_REACHED, ex.errorCode());
            assertEquals(Map.of("maxReschedules", "2", "rescheduleCount", "2", "suggestedAction",
                    "CANCEL_AND_CREATE_NEW"), ex.details());
            verify(bookings, never()).reschedule(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("HU-014: moving to a location where the service is not offered is a 400 naming locationId")
        void locationWithoutTheServiceIsRejected() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 1, 0));
            when(bookings.offering(SERVICE, otherLocation)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, otherLocation,
                    null, null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("locationId", "service is not offered at this location"), ex.details());
        }

        @Test
        @DisplayName("HU-014: a new time that is not free keeps the booking intact (SLOT_UNAVAILABLE)")
        void takenSlotIsUnavailable() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 1, 0));
            freeSlots(null, slot(next.plusSeconds(1800)));

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null, null));

            // Assert
            assertEquals(ErrorCode.SLOT_UNAVAILABLE, ex.errorCode());
            verify(bookings, never()).reschedule(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("HU-014: a requested resource that the free slot does not include is SLOT_UNAVAILABLE")
        void requestedResourceMustBeInTheSlot() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 1, 0));
            UUID chair = UUID.randomUUID();
            freeSlots(chair, slot(next));

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, next, null, chair, null));

            // Assert
            assertEquals(ErrorCode.SLOT_UNAVAILABLE, ex.errorCode());
        }

        @Test
        @DisplayName("HU-014: rescheduling to the very same time, place and resource is a 400, not a reschedule")
        void noOpIsRejected() {
            // Arrange
            locked(target(BookingStatus.CONFIRMED, current, 1, 0));
            when(bookings.slotsForReschedule(offer, current.atZone(BOGOTA).toLocalDate(), NOW, BOOKING, null))
                    .thenReturn(List.of(slot(current)));

            // Act
            BusinessException ex = rejected(() -> useCase.rescheduleByClient(CLIENT, BOOKING, current, null, null,
                    null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("startsAt", "must change date, time, location or resource"), ex.details());
            verify(bookings, never()).reschedule(any(), any(), any(), anyBoolean());
        }

        @ParameterizedTest(name = "starts in {0} s -> late {1}")
        @CsvSource({"3600, false", "3599, true"})
        @DisplayName("HU-014: without a location the booking stays where it is; inside the free window it is marked late")
        void reschedulesInPlaceMarkingLateness(long secondsToStart, boolean late) {
            // Arrange
            RescheduleTarget target = target(BookingStatus.CONFIRMED, NOW.plusSeconds(secondsToStart), 1, 0);
            locked(target);
            Slot free = slot(next);
            freeSlots(null, free);
            RescheduleReceipt receipt = new RescheduleReceipt(BOOKING, "RES-1", BookingStatus.CONFIRMED, LOCATION, next,
                    next.plusSeconds(1800), 1, late);
            when(bookings.reschedule(target, LOCATION, free, late)).thenReturn(receipt);

            // Act
            RescheduleReceipt moved = useCase.rescheduleByClient(CLIENT, BOOKING, next, null, null, SERVICE);

            // Assert
            assertSame(receipt, moved);
            verify(bookings).lockAllocation(SERVICE, LOCATION, CLIENT);
            verify(audit).identify(CLIENT, "CLIENT");
        }

        @Test
        @DisplayName("HU-014: the booking can move to another location that offers the same service, at the same time")
        void movesToAnotherLocation() {
            // Arrange
            RescheduleTarget target = target(BookingStatus.RESERVED, current, 1, 0);
            locked(target);
            BookingOffering elsewhere = offering(false);
            when(bookings.offering(SERVICE, otherLocation)).thenReturn(Optional.of(elsewhere));
            Slot sameTime = slot(current);
            when(bookings.slotsForReschedule(elsewhere, current.atZone(BOGOTA).toLocalDate(), NOW, BOOKING, null))
                    .thenReturn(List.of(sameTime));
            RescheduleReceipt receipt = new RescheduleReceipt(BOOKING, "RES-1", BookingStatus.RESERVED, otherLocation,
                    current, current.plusSeconds(1800), 1, false);
            when(bookings.reschedule(target, otherLocation, sameTime, false)).thenReturn(receipt);

            // Act
            RescheduleReceipt moved = useCase.rescheduleByClient(CLIENT, BOOKING, current, otherLocation, null, null);

            // Assert
            assertSame(receipt, moved);
            verify(bookings).lockAllocation(SERVICE, otherLocation, CLIENT);
        }
    }

    @Nested
    @DisplayName("HU-016 attendance")
    class Attendance {

        private AttendanceTarget target(BookingStatus status, long secondsToStart, Instant closedAt) {
            Instant start = NOW.plusSeconds(secondsToStart);
            return new AttendanceTarget(BOOKING, "RES-1", CLIENT, ORG, status, start, start.plusSeconds(1800), closedAt);
        }

        private void locked(AttendanceTarget target) {
            when(bookings.lockForAttendance(ORG, BOOKING)).thenReturn(Optional.of(target));
        }

        @ParameterizedTest(name = "closing as {0}")
        @EnumSource(value = BookingStatus.class, names = {"RESERVED", "CONFIRMED", "CANCELLED"})
        @DisplayName("HU-016: attendance closes only as COMPLETED or NO_SHOW (400 naming status)")
        void onlyClosingStatusesAreAccepted(BookingStatus status) {
            // Arrange
            member(MemberRole.STAFF);

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR, status, null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("status", "must be COMPLETED or NO_SHOW"), ex.details());
            verify(bookings, never()).lockForAttendance(any(), any());
        }

        @Test
        @DisplayName("HU-016: someone outside the provider cannot mark attendance and does not learn the booking exists")
        void outsiderIsNotFound() {
            // Arrange
            when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.empty());
            locked(target(BookingStatus.CONFIRMED, -3600, null));

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR,
                    BookingStatus.COMPLETED, null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verify(bookings, never()).closeAttendance(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-016: a booking of another provider is not found")
        void bookingOfAnotherProviderIsNotFound() {
            // Arrange
            member(MemberRole.STAFF);
            when(bookings.lockForAttendance(ORG, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR,
                    BookingStatus.COMPLETED, null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @Test
        @DisplayName("HU-016: a cancelled booking cannot be marked as completed or no-show")
        void cancelledBookingIsAConflict() {
            // Arrange
            member(MemberRole.STAFF);
            locked(target(BookingStatus.CANCELLED, -3600, null));

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR,
                    BookingStatus.NO_SHOW, null));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).closeAttendance(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-016: attendance cannot be recorded one second before the start time")
        void beforeStartIsAConflict() {
            // Arrange
            member(MemberRole.STAFF);
            locked(target(BookingStatus.CONFIRMED, 1, null));

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR,
                    BookingStatus.COMPLETED, null));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).closeAttendance(any(), any(), any(), any(), any());
        }

        @ParameterizedTest(name = "observation [{0}] -> [{1}]")
        @CsvSource(value = {
                "NULL | NULL",
                "'   ' | NULL",
                "'  Cliente atendido a tiempo  ' | Cliente atendido a tiempo"
        }, delimiter = '|', nullValues = "NULL")
        @DisplayName("HU-016: staff closes a booking from its start time on, with the actor, the time and an optional trimmed observation")
        void staffClosesAtStartTime(String observation, String stored) {
            // Arrange
            member(MemberRole.STAFF);
            AttendanceTarget target = target(BookingStatus.CONFIRMED, 0, null);
            locked(target);
            AttendanceReceipt receipt = new AttendanceReceipt(BOOKING, "RES-1", BookingStatus.COMPLETED, ACTOR, NOW,
                    stored, 0);
            when(bookings.closeAttendance(target, BookingStatus.COMPLETED, ACTOR, NOW, stored)).thenReturn(receipt);

            // Act
            AttendanceReceipt closed = useCase.recordAttendance(ORG, BOOKING, ACTOR, BookingStatus.COMPLETED,
                    observation);

            // Assert
            assertSame(receipt, closed);
        }

        @Test
        @DisplayName("HU-016: closing again with the same status is a 409, not a second record")
        void sameClosureTwiceIsAConflict() {
            // Arrange
            member(MemberRole.MANAGER);
            locked(target(BookingStatus.NO_SHOW, -7200, NOW.minusSeconds(3600)));

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR,
                    BookingStatus.NO_SHOW, null));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(bookings, never()).closeAttendance(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-016: within 48 hours a closure can be corrected to the other status")
        void correctionWithinTheWindow() {
            // Arrange
            member(MemberRole.MANAGER);
            AttendanceTarget target = target(BookingStatus.NO_SHOW, -200000, NOW.minusSeconds(48 * 3600L));
            locked(target);
            AttendanceReceipt receipt = new AttendanceReceipt(BOOKING, "RES-1", BookingStatus.COMPLETED, ACTOR, NOW,
                    "Sí asistió", 0);
            when(bookings.closeAttendance(target, BookingStatus.COMPLETED, ACTOR, NOW, "Sí asistió")).thenReturn(receipt);

            // Act
            AttendanceReceipt corrected = useCase.recordAttendance(ORG, BOOKING, ACTOR, BookingStatus.COMPLETED,
                    "Sí asistió");

            // Assert
            assertSame(receipt, corrected);
        }

        @ParameterizedTest(name = "correcting to {0}")
        @EnumSource(value = BookingStatus.class, names = {"COMPLETED", "NO_SHOW"})
        @DisplayName("HU-016: after 48 hours the closure can no longer be corrected, whatever status is sent")
        void correctionAfterTheWindowIsRejected(BookingStatus status) {
            // Arrange
            member(MemberRole.OWNER);
            locked(target(BookingStatus.NO_SHOW, -300000, NOW.minusSeconds(48 * 3600L + 1)));

            // Act
            BusinessException ex = rejected(() -> useCase.recordAttendance(ORG, BOOKING, ACTOR, status, null));

            // Assert
            assertEquals(ErrorCode.CLOSURE_CORRECTION_WINDOW_EXPIRED, ex.errorCode());
            verify(bookings, never()).closeAttendance(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("HU-016: the automatic closure runs against the current time of the clock")
        void autoCloseUsesTheClock() {
            // Arrange
            when(bookings.autoCloseOverdue(NOW)).thenReturn(3);

            // Act
            int closed = useCase.autoCloseUnmarked();

            // Assert
            assertEquals(3, closed);
        }
    }

    @Nested
    @DisplayName("HU-017 client history")
    class History {

        private ClientBookingRecord record(UUID id, BookingStatus status, Instant startsAt, int max, int count) {
            return new ClientBookingRecord(id, "RES-" + id.toString().substring(0, 4), ORG, "Barbería", SERVICE,
                    "Corte", LOCATION, "Sede", "Calle 1", BOGOTA, startsAt, startsAt.plusSeconds(1800), 1,
                    new BigDecimal("45000.00"), "COP", status, WINDOW_MINUTES, max, count, false, false,
                    List.of("Silla 1"));
        }

        private ClientBookingItem only(ClientBookingRecord record) {
            when(bookings.findClientBookings(CLIENT)).thenReturn(List.of(record));
            ClientBookingsView view = useCase.clientBookings(CLIENT);
            return view.upcoming().isEmpty() ? view.history().get(0) : view.upcoming().get(0);
        }

        @Test
        @DisplayName("HU-017: a client without bookings gets the empty message and the way to the catalog")
        void emptyHistory() {
            // Arrange
            when(bookings.findClientBookings(CLIENT)).thenReturn(List.of());

            // Act
            ClientBookingsView view = useCase.clientBookings(CLIENT);

            // Assert
            assertTrue(view.empty());
            assertEquals("Aún no tienes reservas", view.emptyMessage());
            assertEquals("/api/v1/catalog/services", view.catalogPath());
        }

        @Test
        @DisplayName("HU-017: upcoming bookings go soonest first and history goes most recent first")
        void ordersUpcomingAscendingAndHistoryDescending() {
            // Arrange
            UUID later = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID sooner = UUID.fromString("00000000-0000-0000-0000-000000000002");
            UUID older = UUID.fromString("00000000-0000-0000-0000-000000000003");
            UUID recent = UUID.fromString("00000000-0000-0000-0000-000000000004");
            UUID startedNotClosed = UUID.fromString("00000000-0000-0000-0000-000000000005");
            when(bookings.findClientBookings(CLIENT)).thenReturn(List.of(
                    record(later, BookingStatus.CONFIRMED, NOW.plusSeconds(2 * 86400), 1, 0),
                    record(older, BookingStatus.COMPLETED, NOW.minusSeconds(3 * 86400), 1, 0),
                    record(sooner, BookingStatus.RESERVED, NOW.plusSeconds(86400), 1, 0),
                    record(startedNotClosed, BookingStatus.CONFIRMED, NOW.minusSeconds(600), 1, 0),
                    record(recent, BookingStatus.CANCELLED, NOW.plusSeconds(3 * 86400), 1, 0)));

            // Act
            ClientBookingsView view = useCase.clientBookings(CLIENT);

            // Assert
            assertFalse(view.empty());
            assertNull(view.emptyMessage());
            assertEquals(List.of(sooner, later), view.upcoming().stream().map(ClientBookingItem::id).toList());
            assertEquals(List.of(recent, startedNotClosed, older),
                    view.history().stream().map(ClientBookingItem::id).toList());
        }

        @Test
        @DisplayName("HU-017: bookings that start at the same time keep a stable order by id in both lists")
        void sameStartIsOrderedById() {
            // Arrange
            UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
            UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
            UUID fourth = UUID.fromString("00000000-0000-0000-0000-000000000004");
            Instant future = NOW.plusSeconds(86400);
            Instant past = NOW.minusSeconds(86400);
            when(bookings.findClientBookings(CLIENT)).thenReturn(List.of(
                    record(second, BookingStatus.CONFIRMED, future, 1, 0),
                    record(fourth, BookingStatus.COMPLETED, past, 1, 0),
                    record(first, BookingStatus.CONFIRMED, future, 1, 0),
                    record(third, BookingStatus.COMPLETED, past, 1, 0)));

            // Act
            ClientBookingsView view = useCase.clientBookings(CLIENT);

            // Assert
            assertEquals(List.of(first, second), view.upcoming().stream().map(ClientBookingItem::id).toList());
            assertEquals(List.of(third, fourth), view.history().stream().map(ClientBookingItem::id).toList());
        }

        @Test
        @DisplayName("HU-017: each item carries the booking data the client needs (code, provider, service, place, time, price, policy, resources)")
        void itemCarriesTheBookingData() {
            // Arrange
            Instant start = NOW.plusSeconds(86400);
            ClientBookingRecord booking = new ClientBookingRecord(BOOKING, "RES-7", ORG, "Barbería", SERVICE, "Corte",
                    LOCATION, "Sede", "Calle 1", BOGOTA, start, start.plusSeconds(1800), 2, new BigDecimal("90000.00"),
                    "COP", BookingStatus.RESERVED, WINDOW_MINUTES, 3, 1, true, true, List.of("Silla 1"));

            // Act
            ClientBookingItem item = only(booking);

            // Assert
            assertEquals(new ClientBookingItem(BOOKING, "RES-7", ORG, "Barbería", SERVICE, "Corte", LOCATION, "Sede",
                    "Calle 1", start, start.plusSeconds(1800), 2, new BigDecimal("90000.00"), "COP",
                    BookingStatus.RESERVED, WINDOW_MINUTES, 3, 1, true, true, List.of("Silla 1"),
                    new BookingActions(true, null, true, null, false, true, "/api/v1/bookings/" + BOOKING + "/receipt")),
                    item);
        }

        @Test
        @DisplayName("HU-017: an upcoming booking inside the free window can be cancelled and rescheduled directly")
        void upcomingWithinWindowAllowsActions() {
            // Arrange
            ClientBookingRecord booking = record(BOOKING, BookingStatus.CONFIRMED, NOW.plusSeconds(3600), 1, 0);

            // Act
            ClientBookingItem item = only(booking);

            // Assert
            assertEquals(new BookingActions(true, null, true, null, false, true,
                    "/api/v1/bookings/" + BOOKING + "/receipt"), item.actions());
        }

        @Test
        @DisplayName("HU-017: one second inside the penalty window both actions appear disabled with the reason")
        void upcomingOutsideWindowDisablesActionsWithReason() {
            // Arrange
            ClientBookingRecord booking = record(BOOKING, BookingStatus.CONFIRMED, NOW.plusSeconds(3599), 1, 0);

            // Act
            ClientBookingItem item = only(booking);

            // Assert
            assertEquals(new BookingActions(false, "Fuera de la ventana de cancelación sin penalidad",
                    false, "Fuera de la ventana de reprogramación sin penalidad", false, true,
                    "/api/v1/bookings/" + BOOKING + "/receipt"), item.actions());
        }

        @ParameterizedTest(name = "max {0}, count {1} -> can reschedule {2}")
        @CsvSource({"2, 1, true", "2, 2, false"})
        @DisplayName("HU-017: at the reschedule limit only rescheduling is disabled, with its reason")
        void rescheduleLimitDisablesOnlyRescheduling(int max, int count, boolean canReschedule) {
            // Arrange
            ClientBookingRecord booking = record(BOOKING, BookingStatus.CONFIRMED, NOW.plusSeconds(86400), max, count);

            // Act
            ClientBookingItem item = only(booking);

            // Assert
            assertTrue(item.actions().canCancel());
            assertEquals(canReschedule, item.actions().canReschedule());
            assertEquals(canReschedule ? null : "Alcanzaste el número máximo de reprogramaciones permitido",
                    item.actions().rescheduleDisabledReason());
        }

        @ParameterizedTest(name = "{0}, starts in {1} s")
        @CsvSource({"CANCELLED, 86400", "CANCELLED, -86400", "COMPLETED, -86400", "NO_SHOW, -86400",
                "CONFIRMED, -600"})
        @DisplayName("HU-017: a started or inactive booking (even a cancelled future one) can be rebooked and its receipt downloaded, but not changed")
        void historyOffersRebookAndReceipt(BookingStatus status, long secondsToStart) {
            // Arrange
            ClientBookingRecord booking = record(BOOKING, status, NOW.plusSeconds(secondsToStart), 1, 0);

            // Act
            ClientBookingItem item = only(booking);

            // Assert
            assertEquals(new BookingActions(false, "La reserva ya no está activa o ya inició",
                    false, "La reserva ya no está activa o ya inició", true, true,
                    "/api/v1/bookings/" + BOOKING + "/receipt"), item.actions());
        }

        @Test
        @DisplayName("HU-017: another client's booking detail is not found")
        void otherClientsDetailIsNotFound() {
            // Arrange
            when(bookings.findClientBookingDetail(CLIENT, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.clientBookingDetail(CLIENT, BOOKING));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("HU-017 rebook and HU-012 availability")
    class RebookAndAvailability {

        private BookingOffering offer;

        @BeforeEach
        void offer() {
            offer = offering(false);
            when(bookings.offering(SERVICE, LOCATION)).thenReturn(Optional.of(offer));
        }

        private ClientBookingDetail detail() {
            return new ClientBookingDetail(BOOKING, "RES-1", "Ana", "ana@example.com", ORG, "Barbería", SERVICE,
                    "Corte", LOCATION, "Sede", "Calle 1", BOGOTA, NOW.minusSeconds(86400), NOW.minusSeconds(84600), 1,
                    new BigDecimal("45000.00"), "COP", BookingStatus.COMPLETED, 60, 1, 0, false, false, List.of(),
                    List.of());
        }

        /**
         * NOW is 2026-11-24T15:00Z (10:00 in Bogota). 600 min later is 2026-11-25T01:00Z, still the 24th in
         * Bogota; 2160 min later is 2026-11-26T03:00Z, the 25th in Bogota.
         */
        @ParameterizedTest(name = "minimum notice {0} min -> {1}")
        @CsvSource({"600, 2026-11-24", "2160, 2026-11-25"})
        @DisplayName("HU-017: rebooking without a date proposes the first day allowed by the minimum notice, in the provider's zone")
        void rebookDefaultsToTheFirstBookableLocalDay(int minNotice, LocalDate localDay) {
            // Arrange
            when(bookings.findClientBookingDetail(CLIENT, BOOKING)).thenReturn(Optional.of(detail()));
            BookingOffering withNotice = new BookingOffering(SERVICE, "Corte", LOCATION, "Sede", ORG, "Barbería", BOGOTA,
                    new BigDecimal("45000.00"), "COP", 30, 0, 0, 1, UUID.randomUUID(), 3, WINDOW_MINUTES, minNotice,
                    43200, false);
            when(bookings.offering(SERVICE, LOCATION)).thenReturn(Optional.of(withNotice));
            Instant start = localDay.atTime(17, 0).atZone(BOGOTA).toInstant();
            when(bookings.slots(withNotice, localDay, NOW)).thenReturn(List.of(slot(start)));

            // Act
            RebookView view = useCase.rebook(CLIENT, BOOKING, null);

            // Assert
            assertEquals(new RebookView(BOOKING, ORG, "Barbería", SERVICE, "Corte", LOCATION, "Sede", localDay,
                    List.of(new TimeSlot(start.atZone(BOGOTA).toOffsetDateTime(),
                            start.plusSeconds(1800).atZone(BOGOTA).toOffsetDateTime()))), view);
        }

        @Test
        @DisplayName("HU-017: rebooking on a chosen date lists that date's free slots")
        void rebookOnAChosenDate() {
            // Arrange
            when(bookings.findClientBookingDetail(CLIENT, BOOKING)).thenReturn(Optional.of(detail()));
            LocalDate chosen = LocalDate.of(2026, 12, 1);
            when(bookings.slots(offer, chosen, NOW)).thenReturn(List.of());

            // Act
            RebookView view = useCase.rebook(CLIENT, BOOKING, chosen);

            // Assert
            assertEquals(chosen, view.date());
            assertEquals(List.of(), view.availableSlots());
        }

        @Test
        @DisplayName("HU-017: rebooking someone else's booking is not found")
        void rebookOfAnotherClientIsNotFound() {
            // Arrange
            when(bookings.findClientBookingDetail(CLIENT, BOOKING)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = rejected(() -> useCase.rebook(CLIENT, BOOKING, null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }

        @Test
        @DisplayName("HU-012: free slots are shown in the provider's local time with its offset")
        void availabilityInProviderTime() {
            // Arrange
            LocalDate day = LocalDate.of(2026, 11, 26);
            when(bookings.slots(offer, day, NOW)).thenReturn(List.of(slot(Instant.parse("2026-11-26T15:00:00Z"))));

            // Act
            List<TimeSlot> slots = useCase.availability(SERVICE, LOCATION, day);

            // Assert
            assertEquals(List.of(new TimeSlot(OffsetDateTime.parse("2026-11-26T10:00:00-05:00"),
                    OffsetDateTime.parse("2026-11-26T10:30:00-05:00"))), slots);
        }

        @Test
        @DisplayName("HU-012: the preview shows the reviewed terms and the token that create will require")
        void previewCarriesTheTermsToken() {
            // Arrange
            Instant start = Instant.parse("2026-11-26T15:00:00Z");
            when(bookings.slots(offer, LocalDate.of(2026, 11, 26), NOW)).thenReturn(List.of(slot(start)));

            // Act
            Preview preview = useCase.preview(SERVICE, LOCATION, start);

            // Assert
            assertEquals(new Preview("Corte", "Barbería", "Sede", new TimeSlot(
                    OffsetDateTime.parse("2026-11-26T10:00:00-05:00"), OffsetDateTime.parse("2026-11-26T10:30:00-05:00")),
                    new BigDecimal("45000.00"), "COP", 3, WINDOW_MINUTES, false, offer.termsVersion(start)), preview);
        }

        @Test
        @DisplayName("HU-012: previewing a time that is no longer free is SLOT_UNAVAILABLE")
        void previewOfATakenSlot() {
            // Arrange
            Instant start = Instant.parse("2026-11-26T15:00:00Z");
            when(bookings.slots(offer, LocalDate.of(2026, 11, 26), NOW)).thenReturn(List.of());

            // Act
            BusinessException ex = rejected(() -> useCase.preview(SERVICE, LOCATION, start));

            // Assert
            assertEquals(ErrorCode.SLOT_UNAVAILABLE, ex.errorCode());
        }
    }
}
