package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaExceptionConflictException;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-007 "Excepciones y bloqueos de agenda", with the real {@link OrganizationAccess}: only the
 * repositories are doubles.
 *
 * <p>Techniques: decision table role x operation (OWNER and MANAGER create and remove exceptions;
 * STAFF only reads them, ADR-0008; an outsider gets 404 for everything), and the error paths of
 * each operation (agenda of another provider, a second exception on the same date, removing an
 * exception that does not exist).
 */
class ManageAgendaExceptionsUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    /** A Thursday. */
    private static final LocalDate CHRISTMAS_EVE = LocalDate.of(2026, 12, 24);

    private OrganizationRepository organizations;
    private AgendaRepository agendas;
    private AgendaExceptionRepository exceptions;
    private ManageAgendaExceptionsUseCase useCase;
    private final Agenda agenda = new Agenda(UUID.randomUUID(), ORG, UUID.randomUUID(), null, "Agenda", 30,
            LocalDate.of(2026, 11, 1), null, List.of(UUID.randomUUID()),
            List.of(new TimeBlock(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))));

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        agendas = mock(AgendaRepository.class);
        exceptions = mock(AgendaExceptionRepository.class);
        useCase = new ManageAgendaExceptionsUseCase(agendas, exceptions,
                new OrganizationAccess(organizations, mock(AuditActor.class)));
        when(agendas.findById(ORG, agenda.getId())).thenReturn(Optional.of(agenda));
    }

    private void callerIs(MemberRole role) {
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.ofNullable(role));
    }

    @Nested
    @DisplayName("Creating exceptions")
    class Creating {

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(value = MemberRole.class, names = {"OWNER", "MANAGER"})
        @DisplayName("HU-007: the owner or a manager closes a date; the closure is stored for that agenda and date")
        void ownerOrManagerClosesADate(MemberRole role) {
            // Arrange
            callerIs(role);

            // Act
            AgendaException closure = useCase.close(ORG, USER, agenda.getId(), CHRISTMAS_EVE, "Festivo");

            // Assert
            assertEquals(List.of(agenda.getId(), CHRISTMAS_EVE, AgendaException.Type.CLOSED, "Festivo"),
                    List.of(closure.agendaId(), closure.date(), closure.type(), closure.reason()));
            verify(exceptions).save(closure);
        }

        @Test
        @DisplayName("HU-007: special hours are stored with the hours given for that date")
        void specialHoursAreStoredWithTheirHours() {
            // Arrange
            callerIs(MemberRole.OWNER);

            // Act
            AgendaException special = useCase.specialHours(ORG, USER, agenda.getId(), CHRISTMAS_EVE,
                    LocalTime.of(8, 0), LocalTime.of(12, 0), "Horario corto");

            // Assert
            assertEquals(new TimeBlock(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                    special.asBlock());
            assertEquals(agenda.getId(), special.agendaId());
            verify(exceptions).save(special);
        }

        @Test
        @DisplayName("HU-007: a second exception on the same date is a 409 OVERLAPPING_PERIOD that names the date")
        void secondExceptionOnTheSameDateIsAConflict() {
            // Arrange
            callerIs(MemberRole.OWNER);
            doThrow(new AgendaExceptionConflictException(new RuntimeException("uq_agenda_exceptions")))
                    .when(exceptions).save(any());
            UUID agendaId = agenda.getId();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.close(ORG, USER, agendaId, CHRISTMAS_EVE, null));

            // Assert
            assertEquals(ErrorCode.OVERLAPPING_PERIOD, ex.errorCode());
            assertEquals("This agenda already has an exception for 2026-12-24", ex.getMessage());
        }

        @Test
        @DisplayName("HU-007: an agenda of another provider is a 404 and nothing is stored")
        void agendaOfAnotherProviderIsNotFound() {
            // Arrange
            callerIs(MemberRole.OWNER);
            UUID foreign = UUID.randomUUID();
            when(agendas.findById(ORG, foreign)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.specialHours(ORG, USER, foreign, CHRISTMAS_EVE, LocalTime.of(8, 0),
                            LocalTime.of(12, 0), null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            assertEquals("Agenda not found", ex.getMessage());
            verifyNoInteractions(exceptions);
        }
    }

    @Nested
    @DisplayName("Who may change the exceptions (ADR-0008)")
    class Authorization {

        /** The operations of HU-007 that change exceptions. */
        enum Change { CLOSE, SPECIAL_HOURS, DELETE }

        private void perform(Change change) {
            UUID agendaId = agenda.getId();
            switch (change) {
                case CLOSE -> useCase.close(ORG, USER, agendaId, CHRISTMAS_EVE, "Festivo");
                case SPECIAL_HOURS -> useCase.specialHours(ORG, USER, agendaId, CHRISTMAS_EVE, LocalTime.of(8, 0),
                        LocalTime.of(12, 0), null);
                case DELETE -> useCase.delete(ORG, USER, agendaId, UUID.randomUUID());
            }
        }

        @ParameterizedTest(name = "[{index}] {0} by {1} -> {2}")
        @CsvSource({
                "CLOSE,         STAFF, ACCESS_DENIED",
                "SPECIAL_HOURS, STAFF, ACCESS_DENIED",
                "DELETE,        STAFF, ACCESS_DENIED",
                "CLOSE,         ,      RESOURCE_NOT_FOUND",
                "SPECIAL_HOURS, ,      RESOURCE_NOT_FOUND",
                "DELETE,        ,      RESOURCE_NOT_FOUND"
        })
        @DisplayName("HU-007: STAFF gets 403 and an outsider 404 for every change; the agenda and its exceptions are not touched")
        void refusedCallersChangeNothing(Change change, MemberRole role, ErrorCode expected) {
            // Arrange
            callerIs(role);

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> perform(change));

            // Assert
            assertEquals(expected, ex.errorCode());
            verifyNoInteractions(agendas, exceptions);
        }
    }

    @Nested
    @DisplayName("Listing and removing")
    class ListingAndRemoving {

        @Test
        @DisplayName("HU-007: any member, STAFF included, reads the exceptions of the agenda")
        void staffListsTheExceptions() {
            // Arrange
            callerIs(MemberRole.STAFF);
            List<AgendaException> stored = List.of(AgendaException.closed(agenda.getId(), CHRISTMAS_EVE, "Festivo"));
            when(exceptions.findByAgenda(agenda.getId())).thenReturn(stored);

            // Act
            List<AgendaException> listed = useCase.list(ORG, USER, agenda.getId());

            // Assert
            assertSame(stored, listed);
        }

        @Test
        @DisplayName("HU-007: the exceptions of an agenda of another provider are not listed: 404")
        void exceptionsOfAForeignAgendaAreNotListed() {
            // Arrange
            callerIs(MemberRole.OWNER);
            UUID foreign = UUID.randomUUID();
            when(agendas.findById(ORG, foreign)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.list(ORG, USER, foreign));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verifyNoInteractions(exceptions);
        }

        @Test
        @DisplayName("HU-007: an outsider cannot read the exceptions: 404")
        void outsiderCannotListTheExceptions() {
            // Arrange
            callerIs(null);
            UUID agendaId = agenda.getId();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.list(ORG, USER, agendaId));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verifyNoInteractions(agendas, exceptions);
        }

        @Test
        @DisplayName("HU-007: the owner removes an exception of the agenda")
        void ownerRemovesAnException() {
            // Arrange
            callerIs(MemberRole.OWNER);
            AgendaException closure = AgendaException.closed(agenda.getId(), CHRISTMAS_EVE, "Festivo");
            when(exceptions.findOne(agenda.getId(), closure.id())).thenReturn(Optional.of(closure));

            // Act
            useCase.delete(ORG, USER, agenda.getId(), closure.id());

            // Assert
            verify(exceptions).delete(agenda.getId(), closure.id());
        }

        @Test
        @DisplayName("HU-007: removing an exception the agenda does not have is a 404 and deletes nothing")
        void removingAMissingExceptionIsNotFound() {
            // Arrange
            callerIs(MemberRole.MANAGER);
            UUID missing = UUID.randomUUID();
            UUID agendaId = agenda.getId();
            when(exceptions.findOne(agendaId, missing)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.delete(ORG, USER, agendaId, missing));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            assertEquals("Exception not found", ex.getMessage());
            verify(exceptions, never()).delete(any(), any());
        }
    }
}
