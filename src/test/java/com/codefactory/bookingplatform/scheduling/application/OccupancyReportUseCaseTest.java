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
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.OccupancyReport;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
 * HU-019 "Reporte de ocupación": for every agenda of the provider, how many of the slots it
 * offered in the period ended up booked. The real {@link OrganizationAccess} and
 * {@link CatalogLookup} are used; only the repositories are doubles.
 *
 * <p>Techniques: decision table over the caller (OWNER and MANAGER read it; STAFF 403; outsider
 * 404), boundary values on the period (inverted by one day, a single day, 366 and 367 days
 * apart) and equivalence partitioning over what removes a slot from the total (a closed date, a
 * maintenance window) or changes it (special hours), versus what only marks it occupied (a booking).
 */
class OccupancyReportUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** A Monday; the period of most tests runs to the next Monday, both included. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate NEXT_MONDAY = MONDAY.plusWeeks(1);

    private OrganizationRepository organizations;
    private AgendaRepository agendas;
    private AgendaExceptionRepository exceptions;
    private AgendaBookings bookings;
    private ResourceRepository resources;
    private OccupancyReportUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        agendas = mock(AgendaRepository.class);
        exceptions = mock(AgendaExceptionRepository.class);
        bookings = mock(AgendaBookings.class);
        resources = mock(ResourceRepository.class);
        useCase = new OccupancyReportUseCase(agendas, exceptions, bookings,
                new CatalogLookup(mock(ServiceRepository.class), resources),
                new OrganizationAccess(organizations, mock(AuditActor.class)));
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.OWNER));
        when(organizations.locationTimezone(ORG, LOCATION)).thenReturn(Optional.of("America/Bogota"));
    }

    /** Monday 08:00-09:00 in 30-minute slots: two slots each Monday. */
    private static Agenda agenda(String name, UUID resourceId) {
        return new Agenda(UUID.randomUUID(), ORG, LOCATION, resourceId, name, 30, LocalDate.of(2026, 11, 1), null,
                List.of(UUID.randomUUID()),
                List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0))));
    }

    private Agenda onlyAgenda(UUID resourceId) {
        Agenda agenda = agenda("Agenda", resourceId);
        when(agendas.findAll(ORG)).thenReturn(List.of(agenda));
        return agenda;
    }

    private static Instant local(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(BOGOTA).toInstant();
    }

    private static OccupancyReport expected(Agenda agenda, long total, long occupied) {
        return new OccupancyReport(agenda.getId(), agenda.getName(), MONDAY, NEXT_MONDAY, total, occupied);
    }

    @Nested
    @DisplayName("What is counted")
    class Counting {

        @Test
        @DisplayName("HU-019: a booking that spans two slots occupies both; the total is every slot offered in the period")
        void bookingSpanningTwoSlotsOccupiesBoth() {
            // Arrange
            Agenda agenda = onlyAgenda(null);
            when(bookings.between(eq(agenda), any(), any())).thenReturn(
                    List.of(new BookedSlot(UUID.randomUUID(), local(MONDAY, 8, 0), local(MONDAY, 9, 0))));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(agenda, 4, 2)), report);
        }

        @Test
        @DisplayName("HU-007/HU-019: a closed date offers nothing, so it is left out of the total")
        void closedDateIsLeftOutOfTheTotal() {
            // Arrange
            Agenda agenda = onlyAgenda(null);
            when(exceptions.findByAgendaAndDateRange(agenda.getId(), MONDAY, NEXT_MONDAY))
                    .thenReturn(List.of(AgendaException.closed(agenda.getId(), NEXT_MONDAY, "Festivo")));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(agenda, 2, 0)), report);
        }

        @Test
        @DisplayName("HU-007/HU-019: special hours count their own window instead of the weekly block")
        void specialHoursCountTheirOwnWindow() {
            // Arrange
            Agenda agenda = onlyAgenda(null);
            when(exceptions.findByAgendaAndDateRange(agenda.getId(), MONDAY, NEXT_MONDAY))
                    .thenReturn(List.of(AgendaException.specialHours(agenda.getId(), NEXT_MONDAY, LocalTime.of(14, 0),
                            LocalTime.of(16, 0), "Jornada especial")));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(agenda, 6, 0)), report);
        }

        @Test
        @DisplayName("HU-009/HU-019: the slots a resource spends in maintenance are not offered, so they are not in the total")
        void maintenanceIsLeftOutOfTheTotal() {
            // Arrange
            UUID resourceId = UUID.randomUUID();
            Agenda agenda = onlyAgenda(resourceId);
            when(resources.findById(ORG, resourceId)).thenReturn(Optional.of(new Resource(resourceId, ORG,
                    UUID.randomUUID(), LOCATION, "Silla", 1, ResourceStatus.ACTIVE,
                    List.of(new MaintenanceWindow(UUID.randomUUID(), local(MONDAY, 8, 0), local(MONDAY, 9, 0),
                            "Arreglo")))));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(agenda, 2, 0)), report);
        }

        @Test
        @DisplayName("HU-019: one row per agenda of the provider, each with its own bookings read for whole local days")
        void oneRowPerAgendaWithItsOwnBookings() {
            // Arrange
            Agenda first = agenda("Cortes", null);
            Agenda second = agenda("Barbas", null);
            when(agendas.findAll(ORG)).thenReturn(List.of(first, second));
            Instant from = Instant.parse("2026-11-02T05:00:00Z");
            Instant to = Instant.parse("2026-11-10T05:00:00Z");
            when(bookings.between(second, from, to)).thenReturn(
                    List.of(new BookedSlot(UUID.randomUUID(), local(NEXT_MONDAY, 8, 30), local(NEXT_MONDAY, 9, 0))));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(first, 4, 0), expected(second, 4, 1)), report);
            verify(bookings).between(first, from, to);
        }
    }

    @Nested
    @DisplayName("The period and who may read it")
    class PeriodAndAccess {

        @ParameterizedTest(name = "[{index}] {0} days apart")
        @ValueSource(ints = {0, 366})
        @DisplayName("HU-019: a period from a single day up to 366 days apart is accepted")
        void periodsUpToTheLimitAreAccepted(int daysApart) {
            // Arrange
            Agenda agenda = agenda("Agenda", null);
            when(agendas.findAll(ORG)).thenReturn(List.of(agenda));

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, MONDAY.plusDays(daysApart));

            // Assert
            assertEquals(1, report.size());
            assertEquals(MONDAY.plusDays(daysApart), report.get(0).to());
        }

        @ParameterizedTest(name = "[{index}] {0} days apart")
        @ValueSource(ints = {-1, 367})
        @DisplayName("HU-019: an inverted period, or one more than 366 days apart, is a 400 and no agenda is read")
        void periodsOutsideTheLimitAreRejected(int daysApart) {
            // Arrange
            LocalDate to = MONDAY.plusDays(daysApart);

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.report(ORG, USER, MONDAY, to));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verifyNoInteractions(agendas, bookings);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(value = MemberRole.class, names = {"OWNER", "MANAGER"})
        @DisplayName("HU-019 / ADR-0008: the owner and a manager read the report")
        void ownerAndManagerReadTheReport(MemberRole role) {
            // Arrange
            when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(role));
            Agenda agenda = onlyAgenda(null);

            // Act
            List<OccupancyReport> report = useCase.report(ORG, USER, MONDAY, NEXT_MONDAY);

            // Assert
            assertEquals(List.of(expected(agenda, 4, 0)), report);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({"STAFF, ACCESS_DENIED", ", RESOURCE_NOT_FOUND"})
        @DisplayName("HU-019 / ADR-0008: STAFF gets 403 and an outsider 404; no agenda is read")
        void staffAndOutsidersAreRefused(MemberRole role, ErrorCode expected) {
            // Arrange
            when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.ofNullable(role));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.report(ORG, USER, MONDAY, NEXT_MONDAY));

            // Assert
            assertEquals(expected, ex.errorCode());
            verifyNoInteractions(agendas, bookings);
        }
    }
}
