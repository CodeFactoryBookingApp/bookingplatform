package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaConflictException;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-006 use cases: access first, provider-owned references, conflicts as 409, and bookings read at the moment of the change. */
class AgendaUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final TimeBlock MONDAY_MORNING = new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

    private AgendaRepository agendas;
    private OrganizationAccess access;
    private CatalogLookup catalog;

    @BeforeEach
    void setUp() {
        agendas = mock(AgendaRepository.class);
        access = mock(OrganizationAccess.class);
        catalog = mock(CatalogLookup.class);
        when(access.ownsLocation(ORG, LOCATION)).thenReturn(true);
        when(catalog.serviceOfferedAt(ORG, SERVICE, LOCATION)).thenReturn(true);
    }

    private static CreateAgendaUseCase.Definition definition(UUID resourceId, List<UUID> services) {
        return new CreateAgendaUseCase.Definition("  Agenda Poblado ", LOCATION, resourceId, 30,
                LocalDate.of(2026, 11, 1), null, services, List.of(MONDAY_MORNING));
    }

    @Nested
    @DisplayName("Creating an agenda")
    class Create {

        private CreateAgendaUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new CreateAgendaUseCase(agendas, access, catalog);
        }

        @Test
        @DisplayName("HU-006: the owner or a manager creates it with the trimmed name, and it is stored")
        void createsTheAgenda() {
            // Arrange
            UUID resource = UUID.randomUUID();
            when(catalog.resourceAt(ORG, resource, LOCATION)).thenReturn(true);

            // Act
            Agenda agenda = useCase.create(ORG, USER, definition(resource, List.of(SERVICE)));

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            assertEquals("Agenda Poblado", agenda.getName());
            assertEquals(resource, agenda.getResourceId());
            verify(agendas).saveNew(agenda);
        }

        @Test
        @DisplayName("HU-006: the validity period of the request, start and end, is the one the agenda is stored with")
        void validityTravelsToTheStoredAgenda() {
            // Arrange
            LocalDate from = LocalDate.of(2026, 11, 1);
            LocalDate until = LocalDate.of(2026, 12, 31);
            CreateAgendaUseCase.Definition bounded = new CreateAgendaUseCase.Definition("Agenda", LOCATION, null, 30,
                    from, until, List.of(SERVICE), List.of(MONDAY_MORNING));

            // Act
            Agenda agenda = useCase.create(ORG, USER, bounded);

            // Assert
            verify(agendas).saveNew(agenda);
            assertEquals(from, agenda.getValidFrom());
            assertEquals(until, agenda.getValidUntil());
        }

        @Test
        @DisplayName("A resource outside the location and a service not offered there are both reported")
        void foreignResourceAndServiceAreRejected() {
            // Arrange
            UUID resource = UUID.randomUUID();
            UUID otherService = UUID.randomUUID();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.create(ORG, USER, definition(resource, List.of(otherService, SERVICE))));

            // Assert
            assertEquals(java.util.Map.of("resourceId", "not a resource of this provider in that location",
                    "serviceIds[0]", "not a service of this provider offered in that location"), ex.details());
            verify(agendas, never()).saveNew(any());
        }

        @Test
        @DisplayName("A location of another provider is reported alone: resource and services are not even looked up")
        void foreignLocationIsRejected() {
            // Arrange
            when(access.ownsLocation(ORG, LOCATION)).thenReturn(false);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.create(ORG, USER, definition(UUID.randomUUID(), List.of(SERVICE))));

            // Assert
            assertEquals(java.util.Map.of("locationId", "not a location of this provider"), ex.details());
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({"DUPLICATE_NAME, DUPLICATE_NAME", "OVERLAPPING_PERIOD, OVERLAPPING_PERIOD"})
        @DisplayName("What the database refuses becomes the matching 409")
        void conflictsBecome409(AgendaConflictException.Reason reason, ErrorCode expected) {
            // Arrange
            doThrow(new AgendaConflictException(reason, new IllegalStateException())).when(agendas).saveNew(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.create(ORG, USER, definition(null, List.of(SERVICE))));

            // Assert
            assertEquals(expected, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Changing an agenda")
    class Manage {

        private AgendaBookings bookings;
        private ManageAgendaUseCase useCase;
        private Agenda agenda;

        @BeforeEach
        void setUp() {
            bookings = mock(AgendaBookings.class);
            useCase = new ManageAgendaUseCase(agendas, bookings, access, Clock.fixed(NOW, ZoneOffset.UTC));
            agenda = new Agenda(UUID.randomUUID(), ORG, LOCATION, null, "Agenda", 30, LocalDate.of(2026, 11, 1), null,
                    List.of(SERVICE), List.of(MONDAY_MORNING));
            when(agendas.findById(ORG, agenda.getId())).thenReturn(Optional.of(agenda));
            when(access.locationZone(ORG, LOCATION)).thenReturn(ZoneId.of("America/Bogota"));
        }

        @Test
        @DisplayName("New blocks are checked against the bookings of now, in the zone of the location, then stored")
        void replacesTheBlocks() {
            // Arrange: Tuesday 15:00 UTC is 10:00 in Bogota, inside 09:00-13:00 only in the zone of the location
            TimeBlock tuesday = new TimeBlock(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0));
            Instant tuesdayTen = Instant.parse("2026-11-03T15:00:00Z");
            when(bookings.future(agenda, NOW)).thenReturn(List.of(new BookedSlot(UUID.randomUUID(), tuesdayTen,
                    tuesdayTen.plusSeconds(1800))));

            // Act
            Agenda result = useCase.replaceBlocks(ORG, USER, agenda.getId(), List.of(tuesday));

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            verify(bookings).future(agenda, NOW);
            verify(agendas).saveBlocks(agenda);
            assertEquals(List.of(tuesday), result.getBlocks());
        }

        @Test
        @DisplayName("A copy of a day is stored like any other change of the blocks")
        void copiesADay() {
            // Act
            Agenda result = useCase.copyDay(ORG, USER, agenda.getId(), DayOfWeek.MONDAY, Set.of(DayOfWeek.FRIDAY));

            // Assert
            verify(bookings).future(agenda, NOW);
            verify(agendas).saveBlocks(agenda);
            assertEquals(List.of(MONDAY_MORNING, MONDAY_MORNING.on(DayOfWeek.FRIDAY)), result.getBlocks());
        }

        @Test
        @DisplayName("HU-006: with a future booking the granularity is not changed and nothing is stored")
        void granularityWithBookingsIsRefused() {
            // Arrange
            when(bookings.future(agenda, NOW)).thenReturn(List.of(new BookedSlot(UUID.randomUUID(), NOW, NOW)));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.changeGranularity(ORG, USER, agenda.getId(), 15));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            verify(agendas, never()).saveGranularity(any());
        }

        @Test
        @DisplayName("HU-006: without future bookings the granularity changes and is stored")
        void granularityWithoutBookingsIsChanged() {
            // Arrange
            when(bookings.future(agenda, NOW)).thenReturn(List.of());

            // Act
            Agenda result = useCase.changeGranularity(ORG, USER, agenda.getId(), 15);

            // Assert
            assertEquals(15, result.getSlotMinutes());
            verify(bookings).future(agenda, NOW);
            verify(agendas).saveGranularity(agenda);
        }

        /**
         * Conversion of time zone at use-case level: the bookings are instants, the blocks are local
         * time of the location, and the use case must hand the zone of the location to the agenda.
         * Technique: one booking that fits only in Bogota time, one that fits only in UTC.
         */
        @Test
        @DisplayName("HU-006: a copy of a day is checked in the zone of the location: 08:00 Bogota (13:00 UTC) fits 08:00-12:00")
        void copyDayComparesBookingsInTheZoneOfTheLocation() {
            // Arrange: Tuesday 2026-11-03 13:00 UTC is 08:00 in Bogota, inside the copied 08:00-12:00 only there
            Instant tuesdayEightInBogota = Instant.parse("2026-11-03T13:00:00Z");
            when(bookings.future(agenda, NOW)).thenReturn(List.of(new BookedSlot(UUID.randomUUID(),
                    tuesdayEightInBogota, tuesdayEightInBogota.plusSeconds(1800))));

            // Act
            Agenda result = useCase.copyDay(ORG, USER, agenda.getId(), DayOfWeek.MONDAY, Set.of(DayOfWeek.TUESDAY));

            // Assert
            verify(access).locationZone(ORG, LOCATION);
            verify(agendas).saveBlocks(agenda);
            assertEquals(List.of(MONDAY_MORNING, MONDAY_MORNING.on(DayOfWeek.TUESDAY)), result.getBlocks());
        }

        @Test
        @DisplayName("HU-006: 06:00 Bogota (11:00 UTC) is outside the copied 08:00-12:00: the copy is refused and not stored")
        void copyDayRefusesABookingOutsideInLocalTime() {
            // Arrange: in UTC this booking would fit 08:00-12:00; in Bogota it starts at 06:00
            Instant tuesdaySixInBogota = Instant.parse("2026-11-03T11:00:00Z");
            BookedSlot booking = new BookedSlot(UUID.randomUUID(), tuesdaySixInBogota, tuesdaySixInBogota.plusSeconds(1800));
            when(bookings.future(agenda, NOW)).thenReturn(List.of(booking));

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.copyDay(ORG, USER,
                    agenda.getId(), DayOfWeek.MONDAY, Set.of(DayOfWeek.TUESDAY)));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            assertEquals(java.util.Map.of(booking.bookingId().toString(), "2026-11-03 06:00-06:30 (TUESDAY)"),
                    ex.details());
            verify(agendas, never()).saveBlocks(any());
        }

        @Test
        @DisplayName("HU-006: new blocks that overlap are refused by the use case and nothing is stored")
        void overlappingBlocksAreNotStored() {
            // Arrange
            TimeBlock late = new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.replaceBlocks(ORG, USER, agenda.getId(), List.of(MONDAY_MORNING, late)));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verify(agendas, never()).saveBlocks(any());
            assertEquals(List.of(MONDAY_MORNING), agenda.getBlocks());
        }

        @Test
        @DisplayName("HU-022: an agenda of another provider is a 404")
        void foreignAgendaIsNotFound() {
            // Arrange
            UUID foreign = UUID.randomUUID();
            when(agendas.findById(ORG, foreign)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.changeGranularity(ORG, USER, foreign, 15));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }

    @Test
    @DisplayName("Listing requires membership only: any role of the provider may read")
    void listingRequiresMembership() {
        // Arrange
        ListAgendasUseCase useCase = new ListAgendasUseCase(agendas, access);
        Agenda stored = new Agenda(UUID.randomUUID(), ORG, LOCATION, null, "Agenda", 30, LocalDate.of(2026, 11, 1),
                null, List.of(SERVICE), List.of(MONDAY_MORNING));
        when(agendas.findAll(ORG)).thenReturn(List.of(stored));

        // Act
        List<Agenda> found = useCase.all(ORG, USER);

        // Assert
        verify(access).requireMember(ORG, USER);
        assertEquals(List.of(stored), found);
    }
}
