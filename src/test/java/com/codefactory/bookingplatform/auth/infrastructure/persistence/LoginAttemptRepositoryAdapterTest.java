package com.codefactory.bookingplatform.auth.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Translation contract of the login attempt adapter. The JPA repository is mocked on purpose:
 * what is under test is the normalisation and the domain translation, not the database
 * (real persistence is already covered by the Testcontainers integration tests).
 */
class LoginAttemptRepositoryAdapterTest {

    private LoginAttemptJpaRepository jpaRepository;
    private LoginAttemptRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(LoginAttemptJpaRepository.class);
        adapter = new LoginAttemptRepositoryAdapter(jpaRepository);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is stored as \"{1}\"")
    @CsvSource({
            "ana.perez@example.com, ana.perez@example.com",
            "ANA.PEREZ@EXAMPLE.COM, ana.perez@example.com",
            "Ana.Perez@Example.Com,  ana.perez@example.com"
    })
    @DisplayName("The email is normalised to lower case before it is stored, so lockout counts one identity")
    void recordAttemptNormalisesTheEmail(String input, String expected) {
        Instant attemptedAt = Instant.parse("2026-09-22T10:15:30Z");

        adapter.recordAttempt(input, false, attemptedAt);

        ArgumentCaptor<LoginAttemptEntity> captor = ArgumentCaptor.forClass(LoginAttemptEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertEquals(expected, captor.getValue().getEmail());
        assertEquals(attemptedAt, captor.getValue().getAttemptedAt());
    }

    @ParameterizedTest(name = "[{index}] success={0} is persisted as is")
    @CsvSource({"true", "false"})
    @DisplayName("Both successful and failed attempts are recorded, the outcome is kept verbatim")
    void recordAttemptKeepsTheOutcome(boolean success) {
        adapter.recordAttempt("ana.perez@example.com", success, Instant.parse("2026-09-22T10:15:30Z"));

        ArgumentCaptor<LoginAttemptEntity> captor = ArgumentCaptor.forClass(LoginAttemptEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertEquals(success, captor.getValue().isSuccess());
    }

    @Test
    @DisplayName("findFailuresSince queries with the normalised email and the caller's window start")
    void findFailuresSinceNormalisesTheEmailAndForwardsTheWindow() {
        Instant since = Instant.parse("2026-09-22T10:00:00Z");
        when(jpaRepository.findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter(anyString(), any()))
                .thenReturn(List.of());

        adapter.findFailuresSince("ANA.Perez@Example.COM", since);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(jpaRepository).findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter(email.capture(), from.capture());
        assertEquals("ana.perez@example.com", email.getValue());
        assertEquals(since, from.getValue());
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    @DisplayName("findFailuresSince hands the domain plain timestamps, in the order the repository returned them")
    void findFailuresSinceTranslatesEntitiesToInstants() {
        Instant since = Instant.parse("2026-09-22T10:00:00Z");
        Instant first = since.plus(1, ChronoUnit.MINUTES);
        Instant second = since.plus(3, ChronoUnit.MINUTES);
        when(jpaRepository.findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter("ana.perez@example.com", since))
                .thenReturn(List.of(
                        new LoginAttemptEntity("ana.perez@example.com", false, first),
                        new LoginAttemptEntity("ana.perez@example.com", false, second)));

        List<Instant> failures = adapter.findFailuresSince("ana.perez@example.com", since);

        assertEquals(List.of(first, second), failures);
    }

    @Test
    @DisplayName("An account with no recent failures yields an empty list, never null")
    void findFailuresSinceReturnsEmptyListWhenThereAreNoFailures() {
        when(jpaRepository.findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter(anyString(), any()))
                .thenReturn(List.of());

        List<Instant> failures = adapter.findFailuresSince("ana.perez@example.com", Instant.now());

        assertTrue(failures.isEmpty());
    }

    @Test
    @DisplayName("A new login attempt entity carries no identity until the database assigns one")
    void newEntityHasNoIdentityYet() {
        Instant attemptedAt = Instant.parse("2026-09-22T10:15:30Z");

        LoginAttemptEntity entity = new LoginAttemptEntity("ana.perez@example.com", false, attemptedAt);

        assertNull(entity.getId());
        assertEquals("ana.perez@example.com", entity.getEmail());
        assertFalse(entity.isSuccess());
        assertEquals(attemptedAt, entity.getAttemptedAt());
    }
}
