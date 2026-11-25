package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-022 / ADR-0009: changing a platform role. The change is reserved as a pending revision,
 * written to the identity provider, completed and audited; a retry resumes the same revision;
 * the last active administrator cannot lose the role; the actor's session is checked before
 * reserving and again before writing (except on a self-demotion, already fenced).
 *
 * <p>Techniques: state transition (no change / pending / completed), decision table for "is there
 * another administrator", boundary values on the page size (49 / 50 users per page) and equivalence
 * partitioning of the provider failures.
 */
class ChangePlatformRoleUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private PlatformRoleChanges changes;
    private IdentityProviderPort identity;
    private RoleTokenSessionPolicy sessions;
    private ApplicationEventPublisher events;
    private ChangePlatformRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        changes = mock(PlatformRoleChanges.class);
        identity = mock(IdentityProviderPort.class);
        sessions = mock(RoleTokenSessionPolicy.class);
        events = mock(ApplicationEventPublisher.class);
        when(sessions.accepts(any())).thenReturn(true);
        when(changes.find(any())).thenReturn(Optional.empty());
        useCase = new ChangePlatformRoleUseCase(changes, identity, sessions, events,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
    }

    private static Jwt tokenOf(UUID subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject.toString()).build();
    }

    private static PlatformUser user(UUID id, AppRole role) {
        return new PlatformUser(id, role, Instant.parse("2026-01-01T00:00:00Z"), null, null);
    }

    /** Makes the reservation visible to the second transaction, as the database does. */
    private RoleChange reservedChange() {
        ArgumentCaptor<RoleChange> reserved = ArgumentCaptor.forClass(RoleChange.class);
        verify(changes).reserve(reserved.capture());
        return reserved.getValue();
    }

    @Nested
    @DisplayName("Transitions of a role change")
    class Transitions {

        @Test
        @DisplayName("HU-022 / ADR-0009: under the role-change lock, a new change is reserved as pending, written to the provider, completed at the clock time and audited")
        void newChangeIsReservedWrittenCompletedAndAudited() {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.CLIENT));
            List<RoleChange> reservations = new ArrayList<>();
            doAnswer(call -> {
                RoleChange change = call.getArgument(0);
                reservations.add(change);
                when(changes.find(TARGET)).thenReturn(Optional.of(change));
                return null;
            }).when(changes).reserve(any());

            // Act
            useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN));

            // Assert
            RoleChange change = reservations.get(0);
            assertEquals(TARGET, change.userId());
            assertEquals(AppRole.PROVIDER, change.role());
            assertNull(change.completedAt(), "the reservation must be pending");
            assertNotNull(change.revision(), "the reservation must carry a new revision");
            InOrder order = inOrder(changes, identity, events);
            order.verify(changes).lock();
            order.verify(changes).reserve(change);
            order.verify(changes).lock();
            order.verify(identity).changeRole(change);
            order.verify(changes).complete(change, NOW);
            order.verify(events).publishEvent(new AuditEvent(NOW, ADMIN, "ADMIN", "PLATFORM_ROLE_CHANGED",
                    "USER", TARGET, null, "SUCCESS"));
        }

        @Test
        @DisplayName("HU-022: asking for the role the user already has changes nothing and records nothing")
        void sameRoleIsANoOp() {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.PROVIDER));

            // Act
            useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN));

            // Assert
            verify(changes, never()).reserve(any());
            verify(identity, never()).changeRole(any());
            verify(changes, never()).complete(any(), any());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("ADR-0009: resending the same change while it is pending retries it with the same revision")
        void pendingChangeWithTheSameRoleIsRetriedWithItsRevision() {
            // Arrange
            RoleChange pending = new RoleChange(TARGET, UUID.randomUUID(), AppRole.PROVIDER, null);
            when(changes.find(TARGET)).thenReturn(Optional.of(pending));

            // Act
            useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN));

            // Assert
            verify(changes, never()).reserve(any());
            verify(identity, never()).getPlatformUser(any());
            verify(identity).changeRole(pending);
            verify(changes).complete(pending, NOW);
        }

        @Test
        @DisplayName("ADR-0009: a different role while a change is pending is a 409 ROLE_CHANGE_PENDING")
        void pendingChangeWithAnotherRoleIsRefused() {
            // Arrange
            when(changes.find(TARGET)).thenReturn(Optional.of(
                    new RoleChange(TARGET, UUID.randomUUID(), AppRole.PROVIDER, null)));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.ADMIN, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.ROLE_CHANGE_PENDING, ex.errorCode());
            verify(identity, never()).changeRole(any());
        }

        @Test
        @DisplayName("ADR-0009: a completed earlier change does not block a new one, which gets a new revision")
        void completedChangeIsNotResumed() {
            // Arrange
            RoleChange earlier = new RoleChange(TARGET, UUID.randomUUID(), AppRole.PROVIDER, NOW.minusSeconds(60));
            when(changes.find(TARGET)).thenReturn(Optional.of(earlier));
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.PROVIDER));

            // Act
            useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN));

            // Assert
            RoleChange reserved = reservedChange();
            assertEquals(AppRole.CLIENT, reserved.role());
            assertNull(reserved.completedAt());
            assertNotEquals(earlier.revision(), reserved.revision());
        }

        @Test
        @DisplayName("ADR-0009: if another request replaced the reservation, the stale one is not written to the provider")
        void replacedReservationIsNotWritten() {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.CLIENT));
            doAnswer(call -> {
                when(changes.find(TARGET)).thenReturn(Optional.of(
                        new RoleChange(TARGET, UUID.randomUUID(), AppRole.PROVIDER, null)));
                return null;
            }).when(changes).reserve(any());

            // Act
            useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN));

            // Assert
            verify(identity, never()).changeRole(any());
            verify(changes, never()).complete(any(), any());
            verify(events, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    @DisplayName("The actor's session")
    class ActorSession {

        @Test
        @DisplayName("ADR-0009: an actor whose own session is no longer accepted gets 401 and nothing is reserved")
        void rejectedActorSessionReservesNothing() {
            // Arrange
            when(sessions.accepts(any())).thenReturn(false);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.errorCode());
            verify(changes, never()).reserve(any());
            verify(identity, never()).getPlatformUser(any());
        }

        @Test
        @DisplayName("ADR-0009: the actor's session is checked again before writing; if it was fenced meanwhile, the provider is not touched")
        void actorSessionIsCheckedAgainBeforeWriting() {
            // Arrange
            when(sessions.accepts(any())).thenReturn(true, false);
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.CLIENT));
            doAnswer(call -> {
                when(changes.find(TARGET)).thenReturn(Optional.of(call.getArgument(0)));
                return null;
            }).when(changes).reserve(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.errorCode());
            verify(identity, never()).changeRole(any());
        }

        @Test
        @DisplayName("ADR-0009: an administrator demoting themselves is checked only before reserving, since the pending change fences their own token")
        void selfDemotionIsNotFencedByItsOwnPendingChange() {
            // Arrange
            when(identity.getPlatformUser(ADMIN)).thenReturn(user(ADMIN, AppRole.ADMIN));
            when(identity.listPlatformUsers(1, 50)).thenReturn(List.of(user(ADMIN, AppRole.ADMIN), user(OTHER, AppRole.ADMIN)));
            doAnswer(call -> {
                when(changes.find(ADMIN)).thenReturn(Optional.of(call.getArgument(0)));
                return null;
            }).when(changes).reserve(any());

            // Act
            useCase.change(ADMIN, AppRole.CLIENT, tokenOf(ADMIN));

            // Assert
            verify(sessions, times(1)).accepts(any());
            verify(identity).changeRole(any());
        }
    }

    @Nested
    @DisplayName("Last administrator (decision table)")
    class LastAdministrator {

        private void demotingTargetAdmin(List<PlatformUser> firstPage) {
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.ADMIN));
            when(identity.listPlatformUsers(1, 50)).thenReturn(firstPage);
        }

        @Test
        @DisplayName("HU-022: the only administrator cannot lose the role: 409 LAST_ADMINISTRATOR and nothing reserved")
        void onlyAdministratorKeepsTheRole() {
            // Arrange
            demotingTargetAdmin(List.of(user(TARGET, AppRole.ADMIN), user(OTHER, AppRole.CLIENT)));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.LAST_ADMINISTRATOR, ex.errorCode());
            verify(changes, never()).reserve(any());
        }

        @Test
        @DisplayName("HU-022: with another active administrator the demotion goes ahead")
        void anotherActiveAdministratorAllowsTheDemotion() {
            // Arrange
            demotingTargetAdmin(List.of(user(TARGET, AppRole.ADMIN), user(OTHER, AppRole.ADMIN)));

            // Act
            useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN));

            // Assert
            assertEquals(AppRole.CLIENT, reservedChange().role());
        }

        @ParameterizedTest(name = "[{index}] other admin''s change to {0}, pending={1} -> allowed={2}")
        @CsvSource({
                "ADMIN,  false, true",
                "ADMIN,  true,  false",
                "CLIENT, false, false",
                "CLIENT, true,  false"
        })
        @DisplayName("ADR-0009: another administrator counts only without a pending change and with ADMIN as its last completed role")
        void otherAdministratorCountsOnlyWithoutAPendingChange(AppRole changedTo, boolean pending, boolean allowed) {
            // Arrange
            demotingTargetAdmin(List.of(user(TARGET, AppRole.ADMIN), user(OTHER, AppRole.ADMIN)));
            when(changes.find(OTHER)).thenReturn(Optional.of(
                    new RoleChange(OTHER, UUID.randomUUID(), changedTo, pending ? null : NOW.minusSeconds(3600))));

            // Act
            Executable act = () -> useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN));

            // Assert
            if (allowed) {
                assertDoesNotThrow(act);
            } else {
                assertEquals(ErrorCode.LAST_ADMINISTRATOR, assertThrows(BusinessException.class, act).errorCode());
            }
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "not confirmed,         ,                     ",
                "banned until later,    2026-11-22T00:00:00Z, ",
                "deleted,               ,                     2026-11-01T00:00:00Z"
        })
        @DisplayName("ADR-0009: an unconfirmed, banned or deleted administrator does not count as another administrator")
        void inactiveAdministratorDoesNotCount(String label, Instant bannedUntil, Instant deletedAt) {
            // Arrange
            Instant confirmedAt = "not confirmed".equals(label) ? null : Instant.parse("2026-01-01T00:00:00Z");
            demotingTargetAdmin(List.of(user(TARGET, AppRole.ADMIN),
                    new PlatformUser(OTHER, AppRole.ADMIN, confirmedAt, bannedUntil, deletedAt)));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.LAST_ADMINISTRATOR, ex.errorCode());
        }

        @Test
        @DisplayName("ADR-0009 (BVA, full page of 50): the next page is read and an administrator there counts")
        void fullPageLeadsToTheNextPage() {
            // Arrange
            List<PlatformUser> fullPage = new ArrayList<>();
            fullPage.add(user(TARGET, AppRole.ADMIN));
            for (int i = 1; i < 50; i++) {
                fullPage.add(user(UUID.randomUUID(), AppRole.CLIENT));
            }
            demotingTargetAdmin(fullPage);
            when(identity.listPlatformUsers(2, 50)).thenReturn(List.of(user(OTHER, AppRole.ADMIN)));

            // Act
            useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN));

            // Assert
            verify(identity).listPlatformUsers(2, 50);
            assertEquals(AppRole.CLIENT, reservedChange().role());
        }

        @Test
        @DisplayName("ADR-0009 (BVA, 49 users): a page shorter than 50 is the last one; no further page is asked")
        void shortPageIsTheLastOne() {
            // Arrange
            List<PlatformUser> page = new ArrayList<>();
            page.add(user(TARGET, AppRole.ADMIN));
            for (int i = 1; i < 49; i++) {
                page.add(user(UUID.randomUUID(), AppRole.CLIENT));
            }
            demotingTargetAdmin(page);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.CLIENT, tokenOf(ADMIN)));

            // Assert
            assertEquals(ErrorCode.LAST_ADMINISTRATOR, ex.errorCode());
            verify(identity, never()).listPlatformUsers(2, 50);
            verify(identity, times(1)).listPlatformUsers(anyInt(), anyInt());
        }

        @Test
        @DisplayName("HU-022: promoting a user to ADMIN never needs the administrator count")
        void promotionDoesNotCountAdministrators() {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.CLIENT));

            // Act
            useCase.change(TARGET, AppRole.ADMIN, tokenOf(ADMIN));

            // Assert
            verify(identity, never()).listPlatformUsers(anyInt(), anyInt());
            assertEquals(AppRole.ADMIN, reservedChange().role());
        }
    }

    @Nested
    @DisplayName("Provider failures")
    class ProviderFailures {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "USER_NOT_FOUND, RESOURCE_NOT_FOUND",
                "RATE_LIMITED,   RATE_LIMITED",
                "UNAVAILABLE,    UPSTREAM_AUTH_ERROR",
                "TOKEN_INVALID,  UPSTREAM_AUTH_ERROR"
        })
        @DisplayName("HU-022: a provider failure is translated to the API catalogue (404 / 429 / 502)")
        void providerFailureIsTranslated(UpstreamAuthError failure, ErrorCode expected) {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenThrow(new UpstreamAuthException(failure, "boom"));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN)));

            // Assert
            assertEquals(expected, ex.errorCode());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(value = UpstreamAuthError.class, names = {"UNAVAILABLE", "RATE_LIMITED"})
        @DisplayName("ADR-0009: if the provider write fails, the change stays pending: not completed and not audited")
        void failedWriteLeavesTheChangePending(UpstreamAuthError failure) {
            // Arrange
            when(identity.getPlatformUser(TARGET)).thenReturn(user(TARGET, AppRole.CLIENT));
            doAnswer(call -> {
                when(changes.find(TARGET)).thenReturn(Optional.of(call.getArgument(0)));
                return null;
            }).when(changes).reserve(any());
            doThrow(new UpstreamAuthException(failure, "boom")).when(identity).changeRole(any());

            // Act
            assertThrows(BusinessException.class, () -> useCase.change(TARGET, AppRole.PROVIDER, tokenOf(ADMIN)));

            // Assert
            verify(changes, never()).complete(any(), any());
            verify(events, never()).publishEvent(any(Object.class));
        }
    }
}
