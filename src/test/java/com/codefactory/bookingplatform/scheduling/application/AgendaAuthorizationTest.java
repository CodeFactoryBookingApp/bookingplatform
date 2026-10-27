package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-022 / ADR-0008 in the agenda use cases, with the real {@link OrganizationAccess}: only its
 * repository is a double, so the role each use case asks for is what is under test.
 *
 * <p>Technique: decision table role x operation. A member with role STAFF (receptionist) may read
 * the agendas but not create or change them (403 ACCESS_DENIED, naming the role); someone who is
 * not a member of the provider gets 404 RESOURCE_NOT_FOUND for every operation, so the agenda's
 * existence is not revealed. In both refusals nothing is read from or written to the agendas.
 */
class AgendaAuthorizationTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final TimeBlock MONDAY_MORNING =
            new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

    private OrganizationRepository organizations;
    private AgendaRepository agendas;
    private AgendaBookings bookings;
    private CatalogLookup catalog;
    private CreateAgendaUseCase create;
    private ManageAgendaUseCase manage;
    private ListAgendasUseCase list;
    private final Agenda stored = new Agenda(UUID.randomUUID(), ORG, LOCATION, null, "Agenda", 30,
            LocalDate.of(2026, 11, 1), null, List.of(SERVICE), List.of(MONDAY_MORNING));

    /** The operations of HU-006 that change agendas: reserved to OWNER and MANAGER. */
    enum Change {
        CREATE, REPLACE_BLOCKS, COPY_DAY, CHANGE_GRANULARITY
    }

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        agendas = mock(AgendaRepository.class);
        bookings = mock(AgendaBookings.class);
        catalog = mock(CatalogLookup.class);
        OrganizationAccess access = new OrganizationAccess(organizations);
        create = new CreateAgendaUseCase(agendas, access, catalog);
        manage = new ManageAgendaUseCase(agendas, bookings, access,
                Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC));
        list = new ListAgendasUseCase(agendas, access);
    }

    private void perform(Change change) {
        UUID agendaId = stored.getId();
        switch (change) {
            case CREATE -> create.create(ORG, USER, new CreateAgendaUseCase.Definition("Agenda", LOCATION, null, 30,
                    LocalDate.of(2026, 11, 1), null, List.of(SERVICE), List.of(MONDAY_MORNING)));
            case REPLACE_BLOCKS -> manage.replaceBlocks(ORG, USER, agendaId, List.of(MONDAY_MORNING));
            case COPY_DAY -> manage.copyDay(ORG, USER, agendaId, DayOfWeek.MONDAY, Set.of(DayOfWeek.FRIDAY));
            case CHANGE_GRANULARITY -> manage.changeGranularity(ORG, USER, agendaId, 15);
        }
    }

    @ParameterizedTest(name = "[{index}] STAFF -> {0}")
    @EnumSource(Change.class)
    @DisplayName("HU-022: a receptionist (STAFF) cannot create or change an agenda: 403 naming the role, nothing touched")
    void staffCannotChangeAgendas(Change change) {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.STAFF));

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> perform(change));

        // Assert
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
        assertEquals("Your role in this organization (STAFF) does not allow this operation", ex.getMessage());
        verifyNoInteractions(agendas, bookings, catalog);
    }

    @ParameterizedTest(name = "[{index}] outsider -> {0}")
    @EnumSource(Change.class)
    @DisplayName("ADR-0008: someone of another provider trying to change its agendas gets 404, not 403")
    void outsiderCannotChangeAgendas(Change change) {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> perform(change));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verifyNoInteractions(agendas, bookings, catalog);
    }

    @Test
    @DisplayName("HU-022: a receptionist (STAFF) can list the agendas of their provider")
    void staffCanListAgendas() {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.STAFF));
        when(agendas.findAll(ORG)).thenReturn(List.of(stored));

        // Act
        List<Agenda> found = list.all(ORG, USER);

        // Assert
        assertEquals(List.of(stored), found);
    }

    @Test
    @DisplayName("HU-022: a receptionist (STAFF) can read one agenda of their provider")
    void staffCanReadOneAgenda() {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.STAFF));
        when(agendas.findById(ORG, stored.getId())).thenReturn(Optional.of(stored));

        // Act
        Agenda found = list.one(ORG, USER, stored.getId());

        // Assert
        assertSame(stored, found);
    }

    @Test
    @DisplayName("ADR-0008: someone of another provider cannot list its agendas: 404, and they are not read")
    void outsiderCannotListAgendas() {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> list.all(ORG, USER));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verifyNoInteractions(agendas);
    }

    @Test
    @DisplayName("ADR-0008: someone of another provider cannot read one of its agendas: 404, and it is not read")
    void outsiderCannotReadOneAgenda() {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> list.one(ORG, USER, stored.getId()));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verifyNoInteractions(agendas);
    }

    @Test
    @DisplayName("HU-022: a member asking for an agenda that is not of their provider gets 404")
    void agendaOfAnotherProviderIsNotFound() {
        // Arrange: the repository only finds agendas within the organization
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.STAFF));
        UUID foreign = UUID.randomUUID();
        when(agendas.findById(ORG, foreign)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> list.one(ORG, USER, foreign));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        assertEquals("Agenda not found", ex.getMessage());
    }
}
