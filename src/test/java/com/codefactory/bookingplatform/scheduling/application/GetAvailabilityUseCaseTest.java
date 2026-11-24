package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.scheduling.application.GetAvailabilityUseCase.DayAvailability;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailableSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaExceptionRepository;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-008 "Generación de franjas" + HU-011 "Calendario de disponibilidad": the free slots of an
 * agenda, day by day, in the time zone of its location. The real {@link OrganizationAccess} and
 * {@link CatalogLookup} are used; only the repositories are doubles.
 *
 * <p>Techniques: boundary values on the length of the range (to before from, the same day, 62
 * and 63 days apart: "at most 62 days apart", as the API documents it), equivalence partitioning
 * over the caller (any member reads it; an outsider gets 404) and over the kind of agenda (of the
 * whole location, or of one resource with maintenance windows).
 */
class GetAvailabilityUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** A Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private OrganizationRepository organizations;
    private AgendaRepository agendas;
    private AgendaExceptionRepository exceptions;
    private AgendaBookings bookings;
    private ResourceRepository resources;
    private GetAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        agendas = mock(AgendaRepository.class);
        exceptions = mock(AgendaExceptionRepository.class);
        bookings = mock(AgendaBookings.class);
        resources = mock(ResourceRepository.class);
        useCase = new GetAvailabilityUseCase(agendas, exceptions, bookings,
                new CatalogLookup(mock(ServiceRepository.class), resources),
                new OrganizationAccess(organizations, mock(AuditActor.class)));
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.STAFF));
        when(organizations.locationTimezone(ORG, LOCATION)).thenReturn(Optional.of("America/Bogota"));
    }

    /** Monday 08:00-09:00 in 30-minute slots, valid from 2026-11-01. */
    private Agenda stored(UUID resourceId) {
        Agenda agenda = new Agenda(UUID.randomUUID(), ORG, LOCATION, resourceId, "Agenda", 30,
                LocalDate.of(2026, 11, 1), null, List.of(UUID.randomUUID()),
                List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(agendas.findById(ORG, agenda.getId())).thenReturn(Optional.of(agenda));
        return agenda;
    }

    private static Instant local(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(BOGOTA).toInstant();
    }

    private static AvailableSlot slot(LocalDate date, int hour, int minute) {
        return new AvailableSlot(local(date, hour, minute), local(date, hour, minute).plusSeconds(1800));
    }

    @Nested
    @DisplayName("The calendar")
    class Calendar {

        @Test
        @DisplayName("HU-011: one entry per date from 'from' to 'to', both included, with the slots in the local time of the location")
        void oneEntryPerDateInLocalTime() {
            // Arrange
            Agenda agenda = stored(null);

            // Act
            List<DayAvailability> calendar = useCase.calendar(ORG, USER, agenda.getId(), MONDAY, MONDAY.plusDays(1));

            // Assert
            assertEquals(List.of(
                    new DayAvailability(MONDAY, List.of(slot(MONDAY, 8, 0), slot(MONDAY, 8, 30))),
                    new DayAvailability(MONDAY.plusDays(1), List.of())), calendar);
        }

        @Test
        @DisplayName("HU-008: the bookings are read for whole local days, from 00:00 of 'from' to 00:00 after 'to'")
        void bookingsAreReadForWholeLocalDays() {
            // Arrange
            Agenda agenda = stored(null);

            // Act
            useCase.calendar(ORG, USER, agenda.getId(), MONDAY, MONDAY.plusDays(1));

            // Assert
            verify(bookings).between(agenda, Instant.parse("2026-11-02T05:00:00Z"),
                    Instant.parse("2026-11-04T05:00:00Z"));
        }

        @Test
        @DisplayName("HU-007/HU-011: a booked slot is not free, and a closure empties only its own date")
        void bookingsAndExceptionsApplyToTheirDate() {
            // Arrange
            Agenda agenda = stored(null);
            LocalDate nextMonday = MONDAY.plusWeeks(1);
            when(bookings.between(eq(agenda), any(), any())).thenReturn(
                    List.of(new BookedSlot(UUID.randomUUID(), local(MONDAY, 8, 0), local(MONDAY, 8, 30))));
            when(exceptions.findByAgendaAndDateRange(agenda.getId(), MONDAY, nextMonday))
                    .thenReturn(List.of(AgendaException.closed(agenda.getId(), nextMonday, "Festivo")));

            // Act
            List<DayAvailability> calendar = useCase.calendar(ORG, USER, agenda.getId(), MONDAY, nextMonday);

            // Assert
            assertEquals(new DayAvailability(MONDAY, List.of(slot(MONDAY, 8, 30))), calendar.get(0));
            assertEquals(new DayAvailability(nextMonday, List.of()), calendar.get(7));
        }

        @Test
        @DisplayName("HU-009/HU-008: a resource agenda leaves out the slots its resource spends in maintenance")
        void resourceMaintenanceIsLeftOut() {
            // Arrange
            UUID resourceId = UUID.randomUUID();
            Agenda agenda = stored(resourceId);
            when(resources.findById(ORG, resourceId)).thenReturn(Optional.of(new Resource(resourceId, ORG,
                    UUID.randomUUID(), LOCATION, "Silla", 1, ResourceStatus.ACTIVE,
                    List.of(new MaintenanceWindow(UUID.randomUUID(), local(MONDAY, 8, 0), local(MONDAY, 8, 30),
                            "Arreglo")))));

            // Act
            List<DayAvailability> calendar = useCase.calendar(ORG, USER, agenda.getId(), MONDAY, MONDAY);

            // Assert
            assertEquals(List.of(new DayAvailability(MONDAY, List.of(slot(MONDAY, 8, 30)))), calendar);
        }

        @Test
        @DisplayName("HU-008: an agenda of the whole location does not ask the catalog for maintenance")
        void locationAgendaDoesNotAskForMaintenance() {
            // Arrange
            Agenda agenda = stored(null);

            // Act
            useCase.calendar(ORG, USER, agenda.getId(), MONDAY, MONDAY);

            // Assert
            verifyNoInteractions(resources);
        }
    }

    @Nested
    @DisplayName("The range and who may read it")
    class RangeAndAccess {

        @ParameterizedTest(name = "[{index}] {0} days apart -> {0}+1 dates")
        @ValueSource(ints = {0, 1, 62})
        @DisplayName("HU-011: a range from the same day up to 62 days apart is accepted, one entry per date")
        void rangesUpToTheLimitAreAccepted(int daysApart) {
            // Arrange
            Agenda agenda = stored(null);

            // Act
            List<DayAvailability> calendar = useCase.calendar(ORG, USER, agenda.getId(), MONDAY,
                    MONDAY.plusDays(daysApart));

            // Assert
            assertEquals(daysApart + 1, calendar.size());
            assertEquals(MONDAY.plusDays(daysApart), calendar.get(daysApart).date());
        }

        @ParameterizedTest(name = "[{index}] {0} days apart")
        @ValueSource(ints = {-1, 63})
        @DisplayName("HU-011: an inverted range, or one more than 62 days apart, is a 400 and the agenda is not read")
        void rangesOutsideTheLimitAreRejected(int daysApart) {
            // Arrange
            UUID agendaId = UUID.randomUUID();
            LocalDate to = MONDAY.plusDays(daysApart);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.calendar(ORG, USER, agendaId, MONDAY, to));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verifyNoInteractions(agendas, bookings);
        }

        @Test
        @DisplayName("HU-022: someone outside the provider gets 404 and nothing is read")
        void outsiderGetsNotFound() {
            // Arrange
            UUID outsider = UUID.randomUUID();
            when(organizations.findMemberRole(ORG, outsider)).thenReturn(Optional.empty());
            UUID agendaId = UUID.randomUUID();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.calendar(ORG, outsider, agendaId, MONDAY, MONDAY));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verifyNoInteractions(agendas, exceptions, bookings);
        }

        @Test
        @DisplayName("HU-011: an agenda of another provider is a 404 and no booking is read")
        void foreignAgendaIsNotFound() {
            // Arrange
            UUID foreign = UUID.randomUUID();
            when(agendas.findById(ORG, foreign)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.calendar(ORG, USER, foreign, MONDAY, MONDAY));

            // Assert
            assertEquals("Agenda not found", ex.getMessage());
            verifyNoInteractions(exceptions, bookings);
        }
    }
}
