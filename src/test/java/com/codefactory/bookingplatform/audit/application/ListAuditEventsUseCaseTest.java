package com.codefactory.bookingplatform.audit.application;

import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.audit.domain.port.AuditEvents;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-023: who may read which audit trail, which filters are valid and how a page is cut.
 * The platform trail (no organization) is for administrators; an organization's trail is for its
 * members. Techniques: decision table (scope x admin x member), boundary values on the time range
 * (to = from, to = from + 1 s) and on the action length (0, 1, 64, 65), equivalence partitioning on
 * the page window.
 */
class ListAuditEventsUseCaseTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(UUID.randomUUID(), true);
    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(UUID.randomUUID(), false);
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final AuditFilter NO_FILTER = new AuditFilter(null, null, null, null);

    private AuditEvents events;
    private OrganizationAccess access;
    private ListAuditEventsUseCase useCase;

    @BeforeEach
    void setUp() {
        events = mock(AuditEvents.class);
        access = mock(OrganizationAccess.class);
        useCase = new ListAuditEventsUseCase(events, access);
    }

    private static AuditEntry entry() {
        return new AuditEntry(UUID.randomUUID(), new AuditEvent(FROM, UUID.randomUUID(), "ADMIN",
                "PLATFORM_ROLE_CHANGED", "USER", UUID.randomUUID(), null, "SUCCESS"));
    }

    @Nested
    @DisplayName("Who reads which trail")
    class Scope {

        @Test
        @DisplayName("HU-023: someone who is not an administrator cannot read the platform trail (403) and nothing is queried")
        void nonAdministratorCannotReadThePlatformTrail() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.page(MEMBER, null, NO_FILTER, 0, 20));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verifyNoInteractions(events);
        }

        @Test
        @DisplayName("HU-023: nor export it")
        void nonAdministratorCannotExportThePlatformTrail() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.export(MEMBER, null, NO_FILTER));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verifyNoInteractions(events);
        }

        @Test
        @DisplayName("HU-023: an administrator reads the whole platform trail (no organization scope) without a membership check")
        void administratorReadsThePlatformTrail() {
            // Arrange
            when(events.page(null, NO_FILTER, 20, 0L)).thenReturn(List.of());

            // Act
            useCase.page(ADMIN, null, NO_FILTER, 0, 20);

            // Assert
            verify(events).page(null, NO_FILTER, 20, 0L);
            verifyNoInteractions(access);
        }

        @Test
        @DisplayName("HU-023: an organization's trail requires membership and the export is the whole trail of that organization")
        void organizationTrailRequiresMembership() {
            // Arrange
            List<AuditEntry> trail = List.of(entry(), entry());
            when(events.findAll(ORGANIZATION, NO_FILTER)).thenReturn(trail);

            // Act
            List<AuditEntry> exported = useCase.export(MEMBER, ORGANIZATION, NO_FILTER);

            // Assert
            verify(access).requireMember(ORGANIZATION, MEMBER.id());
            assertEquals(trail, exported);
        }

        @Test
        @DisplayName("HU-023: a non-member gets the membership error and the trail is not read")
        void nonMemberDoesNotReadTheTrail() {
            // Arrange
            when(access.requireMember(ORGANIZATION, MEMBER.id()))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Organization not found"));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.page(MEMBER, ORGANIZATION, NO_FILTER, 0, 20));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verifyNoInteractions(events);
        }
    }

    @Nested
    @DisplayName("Filters")
    class Filters {

        @ParameterizedTest(name = "[{index}] to = from + {0} s -> valid={1}")
        @CsvSource({"-1, false", "0, false", "1, true"})
        @DisplayName("HU-023 (BVA): the range includes from and excludes to, so to must be after from")
        void rangeMustEndAfterItStarts(long seconds, boolean valid) {
            // Arrange
            AuditFilter filter = new AuditFilter(FROM, FROM.plusSeconds(seconds), null, null);

            // Act
            Executable act = () -> useCase.export(ADMIN, null, filter);

            // Assert
            if (valid) {
                assertDoesNotThrow(act);
            } else {
                BusinessException ex = assertThrows(BusinessException.class, act);
                assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
                assertEquals(Map.of("to", "must be after from"), ex.details());
            }
        }

        @Test
        @DisplayName("HU-023: an open range (only from, or only to) is valid")
        void openRangeIsValid() {
            // Act
            useCase.export(ADMIN, null, new AuditFilter(FROM, null, null, null));
            useCase.export(ADMIN, null, new AuditFilter(null, FROM, null, null));

            // Assert
            verify(events).findAll(null, new AuditFilter(FROM, null, null, null));
            verify(events).findAll(null, new AuditFilter(null, FROM, null, null));
        }

        @ParameterizedTest(name = "[{index}] action of {0} characters -> valid={1}")
        @CsvSource({"0, false", "1, true", "64, true", "65, false"})
        @DisplayName("HU-023 (BVA): the action filter has between 1 and 64 characters")
        void actionLengthIsBounded(int length, boolean valid) {
            // Arrange
            AuditFilter filter = new AuditFilter(null, null, null, "A".repeat(length));

            // Act
            Executable act = () -> useCase.export(ADMIN, null, filter);

            // Assert
            if (valid) {
                assertDoesNotThrow(act);
            } else {
                BusinessException ex = assertThrows(BusinessException.class, act);
                assertEquals(Map.of("action", "must contain between 1 and 64 characters"), ex.details());
            }
        }

        @Test
        @DisplayName("HU-023: a blank action is not a filter for \"anything\": it is rejected")
        void blankActionIsRejected() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.export(ADMIN, null, new AuditFilter(null, null, null, "   ")));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verify(events, never()).findAll(any(), any());
        }

        @Test
        @DisplayName("HU-023: every invalid parameter is reported at once (page, size, range and action)")
        void allProblemsAreReportedTogether() {
            // Arrange
            AuditFilter filter = new AuditFilter(FROM, FROM, null, "");

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.page(ADMIN, null, filter, -1, 0));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(List.of("page", "size", "to", "action"), List.copyOf(ex.details().keySet()));
            verify(events, never()).page(any(), any(), anyInt(), anyLong());
        }
    }

    @Nested
    @DisplayName("Paging")
    class Paging {

        @Test
        @DisplayName("HU-023: page n of size s skips n*s events and the total counts every event that matches the same filter")
        void pageIsCutByOffsetAndCounted() {
            // Arrange
            AuditFilter filter = new AuditFilter(null, null, null, "BOOKING_CONFIRMED");
            List<AuditEntry> items = List.of(entry(), entry());
            when(events.page(ORGANIZATION, filter, 20, 40L)).thenReturn(items);
            when(events.count(ORGANIZATION, filter)).thenReturn(42L);

            // Act
            ResultPage<AuditEntry> page = useCase.page(MEMBER, ORGANIZATION, filter, 2, 20);

            // Assert
            assertEquals(new ResultPage<>(items, 42L, 2, 20), page);
        }

        @Test
        @DisplayName("HU-023: the offset is computed in long arithmetic, so a large page number does not overflow")
        void largeOffsetDoesNotOverflow() {
            // Arrange
            long expectedOffset = 30_000_000L * 100;

            // Act
            useCase.page(ADMIN, null, NO_FILTER, 30_000_000, 100);

            // Assert
            verify(events).page(null, NO_FILTER, 100, expectedOffset);
        }
    }
}
