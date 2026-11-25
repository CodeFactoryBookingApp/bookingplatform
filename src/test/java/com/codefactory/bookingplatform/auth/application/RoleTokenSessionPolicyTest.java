package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-022 / ADR-0009: which tokens survive a role change. Without a recorded change every token is
 * accepted; while a change is pending none is; after it completes a token must carry the new role and
 * revision in {@code app_metadata} and every {@code amr} entry must be later than the completion second.
 *
 * <p>Techniques: state transition (no change / pending / completed), decision table over the claims
 * and boundary values on the {@code amr} timestamp (completion second -1, equal, +1).
 */
class RoleTokenSessionPolicyTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REVISION = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    /** Completed at 15:00:00.700; Supabase timestamps have second precision, so the cutoff second is 15:00:00. */
    private static final Instant COMPLETED = Instant.parse("2026-11-21T15:00:00.700Z");
    private static final long CUTOFF = COMPLETED.getEpochSecond();

    private PlatformRoleChanges changes;
    private RoleTokenSessionPolicy policy;

    @BeforeEach
    void setUp() {
        changes = mock(PlatformRoleChanges.class);
        when(changes.find(USER)).thenReturn(Optional.empty());
        policy = new RoleTokenSessionPolicy(changes);
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "none").subject(USER.toString());
    }

    private static Jwt tokenAfterChange(String role, String revision, Object amr) {
        Jwt.Builder jwt = token().claim("app_metadata", Map.of("role", role, RoleChange.REVISION_CLAIM, revision));
        if (amr != null) {
            jwt.claim("amr", amr);
        }
        return jwt.build();
    }

    private static List<Map<String, Object>> signedInAt(long... seconds) {
        return java.util.Arrays.stream(seconds)
                .mapToObj(second -> Map.<String, Object>of("method", "password", "timestamp", second)).toList();
    }

    private void completedChangeTo(AppRole role) {
        when(changes.find(USER)).thenReturn(Optional.of(new RoleChange(USER, REVISION, role, COMPLETED)));
    }

    @Test
    @DisplayName("ADR-0009: a user whose role never changed through the API keeps every token")
    void userWithoutRoleChangeIsAccepted() {
        // Arrange
        Jwt jwt = token().claim("app_metadata", Map.of("role", "CLIENT")).build();

        // Act
        boolean accepted = policy.accepts(jwt);

        // Assert
        assertTrue(accepted);
    }

    @Test
    @DisplayName("ADR-0009: while the change is pending every token of the user is rejected, even one that already carries the new revision")
    void pendingChangeRejectsEveryToken() {
        // Arrange
        when(changes.find(USER)).thenReturn(Optional.of(new RoleChange(USER, REVISION, AppRole.PROVIDER, null)));

        // Act
        boolean accepted = policy.accepts(tokenAfterChange("PROVIDER", REVISION.toString(), signedInAt(CUTOFF + 10)));

        // Assert
        assertFalse(accepted);
    }

    @ParameterizedTest(name = "[{index}] subject \"{0}\"")
    @ValueSource(strings = {"not-a-uuid", "service_role"})
    @DisplayName("ADR-0009: a token whose subject is not a user id is rejected without looking for role changes")
    void malformedSubjectIsRejected(String subject) {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();

        // Act
        boolean accepted = policy.accepts(jwt);

        // Assert
        assertFalse(accepted);
        verify(changes, never()).find(any());
    }

    @Test
    @DisplayName("ADR-0009: a token without a subject is rejected, not a server error")
    void missingSubjectIsRejected() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("email", "ana@example.com").build();

        // Act
        boolean accepted = policy.accepts(jwt);

        // Assert
        assertFalse(accepted);
    }

    @Nested
    @DisplayName("After a completed change (decision table on the claims)")
    class AfterCompletedChange {

        @Test
        @DisplayName("ADR-0009: a new session with the new role and revision, signed in after the change, is accepted")
        void newSessionIsAccepted() {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);

            // Act
            boolean accepted = policy.accepts(tokenAfterChange("PROVIDER", REVISION.toString(), signedInAt(CUTOFF + 1)));

            // Assert
            assertTrue(accepted);
        }

        @ParameterizedTest(name = "[{index}] role={0} revision={1}")
        @CsvSource({
                "CLIENT,   00000000-0000-0000-0000-0000000000ff",
                "PROVIDER, 00000000-0000-0000-0000-0000000000ee",
                "provider, 00000000-0000-0000-0000-0000000000ff"
        })
        @DisplayName("ADR-0009: a token with the old role or another revision is rejected even if signed in later")
        void staleMetadataIsRejected(String role, String revision) {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);

            // Act
            boolean accepted = policy.accepts(tokenAfterChange(role, revision, signedInAt(CUTOFF + 1)));

            // Assert
            assertFalse(accepted);
        }

        @Test
        @DisplayName("ADR-0009: a token without app_metadata is rejected once the user has a completed change")
        void missingMetadataIsRejected() {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);
            Jwt jwt = token().claim("amr", signedInAt(CUTOFF + 1)).build();

            // Act
            boolean accepted = policy.accepts(jwt);

            // Assert
            assertFalse(accepted);
        }

        @ParameterizedTest(name = "[{index}] signed in at cutoff{0}")
        @CsvSource({"-1, false", "0, false", "1, true"})
        @DisplayName("ADR-0009 (BVA): a refreshed token keeps its original sign-in time; only one later than the completion second is accepted")
        void signInMustBeLaterThanTheCompletionSecond(long offset, boolean expected) {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);

            // Act
            boolean accepted = policy.accepts(tokenAfterChange("PROVIDER", REVISION.toString(), signedInAt(CUTOFF + offset)));

            // Assert
            assertTrue(accepted == expected, "accepted=" + accepted);
        }

        @Test
        @DisplayName("ADR-0009: every authentication method must be later than the change; one earlier entry rejects the token")
        void everyAuthenticationMethodMustBeLater() {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);

            // Act
            boolean accepted = policy.accepts(tokenAfterChange("PROVIDER", REVISION.toString(),
                    signedInAt(CUTOFF + 5, CUTOFF - 5)));

            // Assert
            assertFalse(accepted);
        }

        @Test
        @DisplayName("ADR-0009: without amr, with an empty amr, or with an entry lacking a numeric timestamp, the sign-in time is unknown and the token is rejected")
        void unknownSignInTimeIsRejected() {
            // Arrange
            completedChangeTo(AppRole.PROVIDER);
            String revision = REVISION.toString();

            // Act
            List<Boolean> accepted = List.of(
                    policy.accepts(tokenAfterChange("PROVIDER", revision, null)),
                    policy.accepts(tokenAfterChange("PROVIDER", revision, List.of())),
                    policy.accepts(tokenAfterChange("PROVIDER", revision,
                            List.of(Map.of("method", "password", "timestamp", String.valueOf(CUTOFF + 5))))),
                    policy.accepts(tokenAfterChange("PROVIDER", revision, List.of("password"))),
                    policy.accepts(tokenAfterChange("PROVIDER", revision, "password")));

            // Assert
            org.junit.jupiter.api.Assertions.assertEquals(List.of(false, false, false, false, false), accepted);
        }
    }
}
