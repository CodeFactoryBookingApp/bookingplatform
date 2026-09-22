package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.LoginAttemptRepository;
import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the login business rules (HU-021).
 *
 * Techniques applied, declared per group:
 *  - Boundary value analysis on the failed-attempt lock threshold.
 *  - Equivalence partitioning and decision tables on which upstream errors count
 *    as a failed attempt, and on the upstream-error to ErrorCode mapping.
 *  - Branch and condition coverage: both operands of the compound OR in the catch
 *    block, every arm of the mapping switch, both sides of the lock guard.
 *  - Interaction verification: the provider must not be reached while locked, and
 *    the attempt must be recorded with the normalized email.
 */
class LoginUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    private IdentityProviderPort identityProvider;
    private LoginAttemptRepository loginAttemptRepository;
    private LoginUseCase useCase;

    private final AuthTokens tokens = new AuthTokens("access", "refresh", "bearer", 3600L);

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new LoginUseCase(identityProvider, loginAttemptRepository,
                new LoginLockPolicy(MAX_FAILED_ATTEMPTS, LOCK_WINDOW), clock);
    }

    /** Recent failures inside the sliding window, one per minute before now. */
    private void givenRecentFailures(int count) {
        List<Instant> failures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            failures.add(NOW.minus(Duration.ofMinutes(i)));
        }
        when(loginAttemptRepository.findFailuresSince(anyString(), any(Instant.class))).thenReturn(failures);
    }

    private UpstreamAuthException upstream(UpstreamAuthError error) {
        return new UpstreamAuthException(error, "gotrue said " + error);
    }

    // ----------------------------------------------------------------- //
    // Black box: boundary value analysis, maxFailedAttempts -1 / = / +1  //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Lock threshold (boundary value analysis around maxFailedAttempts = 3)")
    class LockThreshold {

        @Test
        @DisplayName("One failure below the threshold still lets the user log in")
        void oneBelowThresholdIsNotLocked() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS - 1);
            when(identityProvider.requestPasswordToken("ana@example.com", "pwd")).thenReturn(tokens);

            assertSame(tokens, useCase.login("ana@example.com", "pwd"));
        }

        @Test
        @DisplayName("Exactly maxFailedAttempts recent failures lock the account")
        void exactlyAtThresholdIsLocked() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.errorCode());
        }

        @Test
        @DisplayName("One failure above the threshold keeps the account locked")
        void oneAboveThresholdIsLocked() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS + 1);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.errorCode());
        }

        @Test
        @DisplayName("An account with no previous failures is never locked")
        void noFailuresIsNotLocked() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            assertSame(tokens, useCase.login("ana@example.com", "pwd"));
        }

        @Test
        @DisplayName("Failures older than the lock window do not lock the account")
        void staleFailuresOutsideWindowDoNotLock() {
            when(loginAttemptRepository.findFailuresSince(anyString(), any(Instant.class)))
                    .thenReturn(List.of(NOW.minus(Duration.ofMinutes(16)),
                            NOW.minus(Duration.ofMinutes(17)),
                            NOW.minus(Duration.ofMinutes(18))));
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            assertSame(tokens, useCase.login("ana@example.com", "pwd"));
        }
    }

    // ----------------------------------------------------------------- //
    // White box: the locked branch, plus interaction verification        //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Lock response contract")
    class LockResponse {

        @Test
        @DisplayName("A locked account never reaches the identity provider")
        void lockedAccountDoesNotCallProvider() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS);

            assertThrows(BusinessException.class, () -> useCase.login("ana@example.com", "pwd"));

            verifyNoInteractions(identityProvider);
        }

        @Test
        @DisplayName("A login rejected by the lock is not recorded as a new attempt")
        void lockedAccountDoesNotRecordAttempt() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS);

            assertThrows(BusinessException.class, () -> useCase.login("ana@example.com", "pwd"));

            verify(loginAttemptRepository, never()).recordAttempt(anyString(), anyBoolean(), any(Instant.class));
        }

        @Test
        @DisplayName("The lock reports retryAfterMinutes as the configured window in minutes")
        void lockReportsRetryAfterMinutes() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(String.valueOf(LOCK_WINDOW.toMinutes()), ex.details().get("retryAfterMinutes"));
        }

        @Test
        @DisplayName("The lock is reported with the ACCOUNT_LOCKED default message")
        void lockUsesDefaultMessage() {
            givenRecentFailures(MAX_FAILED_ATTEMPTS);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(ErrorCode.ACCOUNT_LOCKED.defaultMessage(), ex.getMessage());
        }
    }

    // ----------------------------------------------------------------- //
    // Black box: equivalence partitions on email casing                  //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Email normalization (partitions: upper case, mixed case, already lower case)")
    class EmailNormalization {

        @ParameterizedTest(name = "[{0}] is queried as [{1}]")
        @CsvSource({
                "ANA@EXAMPLE.COM, ana@example.com",
                "Ana.Perez@Example.Com, ana.perez@example.com",
                "ana@example.com, ana@example.com"
        })
        @DisplayName("The email is lowercased before looking up the recent failures")
        void emailIsNormalizedBeforeQuery(String rawEmail, String expected) {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            useCase.login(rawEmail, "pwd");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(loginAttemptRepository).findFailuresSince(captor.capture(), any(Instant.class));
            assertEquals(expected, captor.getValue());
        }

        @ParameterizedTest(name = "[{0}] is recorded as [{1}]")
        @CsvSource({
                "ANA@EXAMPLE.COM, ana@example.com",
                "Ana.Perez@Example.Com, ana.perez@example.com"
        })
        @DisplayName("The email is lowercased before recording a successful attempt")
        void emailIsNormalizedBeforeRecordingSuccess(String rawEmail, String expected) {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            useCase.login(rawEmail, "pwd");

            verify(loginAttemptRepository).recordAttempt(eq(expected), anyBoolean(), any(Instant.class));
        }

        @Test
        @DisplayName("The email is lowercased before recording a failed attempt")
        void emailIsNormalizedBeforeRecordingFailure() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString()))
                    .thenThrow(upstream(UpstreamAuthError.INVALID_CREDENTIALS));

            assertThrows(BusinessException.class, () -> useCase.login("ANA@Example.COM", "pwd"));

            verify(loginAttemptRepository).recordAttempt(eq("ana@example.com"), eq(false), any(Instant.class));
        }

        @Test
        @DisplayName("The normalized email, not the raw one, is sent to the identity provider")
        void normalizedEmailIsSentToProvider() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            useCase.login("ANA@EXAMPLE.COM", "pwd");

            verify(identityProvider).requestPasswordToken("ana@example.com", "pwd");
        }
    }

    @Nested
    @DisplayName("Sliding window query")
    class WindowQuery {

        @Test
        @DisplayName("Recent failures are looked up from now minus the lock window")
        void queriesFailuresSinceNowMinusWindow() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            useCase.login("ana@example.com", "pwd");

            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(loginAttemptRepository).findFailuresSince(anyString(), captor.capture());
            assertEquals(NOW.minus(LOCK_WINDOW), captor.getValue());
        }
    }

    @Nested
    @DisplayName("Successful login (happy path)")
    class SuccessfulLogin {

        @Test
        @DisplayName("A correct login returns the tokens issued by the identity provider")
        void returnsProviderTokens() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            assertSame(tokens, useCase.login("ana@example.com", "pwd"));
        }

        @Test
        @DisplayName("A correct login is recorded as a successful attempt at the current instant")
        void recordsSuccessTrue() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenReturn(tokens);

            useCase.login("ana@example.com", "pwd");

            verify(loginAttemptRepository).recordAttempt("ana@example.com", true, NOW);
        }
    }

    // ----------------------------------------------------------------- //
    // Decision table: which upstream errors count as a failed attempt    //
    // (also covers both operands of the compound OR in the catch block)  //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Failed-attempt bookkeeping (decision table over UpstreamAuthError)")
    class AttemptBookkeeping {

        @ParameterizedTest(name = "{0} counts as a failed attempt")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"INVALID_CREDENTIALS", "EMAIL_NOT_CONFIRMED"})
        @DisplayName("Credential-related upstream errors count towards the lock")
        void credentialErrorsAreRecorded(UpstreamAuthError error) {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenThrow(upstream(error));

            assertThrows(BusinessException.class, () -> useCase.login("ana@example.com", "pwd"));

            verify(loginAttemptRepository).recordAttempt("ana@example.com", false, NOW);
        }

        @ParameterizedTest(name = "{0} does not count as a failed attempt")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"RATE_LIMITED", "UNAVAILABLE", "USER_NOT_FOUND",
                        "USER_ALREADY_EXISTS", "TOKEN_INVALID", "TOKEN_EXPIRED"})
        @DisplayName("Infrastructure or unrelated upstream errors never count towards the lock")
        void nonCredentialErrorsAreNotRecorded(UpstreamAuthError error) {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenThrow(upstream(error));

            assertThrows(BusinessException.class, () -> useCase.login("ana@example.com", "pwd"));

            verify(loginAttemptRepository, never()).recordAttempt(anyString(), anyBoolean(), any(Instant.class));
        }
    }

    // ----------------------------------------------------------------- //
    // White box: every arm of the mapUpstream switch                     //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Upstream error translation (every arm of the mapping switch)")
    class UpstreamMapping {

        @ParameterizedTest(name = "{0} is reported to the caller as {1}")
        @CsvSource({
                "INVALID_CREDENTIALS, INVALID_CREDENTIALS",
                "EMAIL_NOT_CONFIRMED, EMAIL_NOT_CONFIRMED",
                "RATE_LIMITED,        RATE_LIMITED",
                "USER_ALREADY_EXISTS, UPSTREAM_AUTH_ERROR",
                "USER_NOT_FOUND,      UPSTREAM_AUTH_ERROR",
                "TOKEN_INVALID,       UPSTREAM_AUTH_ERROR",
                "TOKEN_EXPIRED,       UPSTREAM_AUTH_ERROR",
                "UNAVAILABLE,         UPSTREAM_AUTH_ERROR"
        })
        @DisplayName("Each upstream error maps to its business error code")
        void mapsEachUpstreamError(UpstreamAuthError error, ErrorCode expected) {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString())).thenThrow(upstream(error));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(expected, ex.errorCode());
        }

        @Test
        @DisplayName("An unmapped upstream failure keeps the provider message for diagnosis")
        void unmappedErrorKeepsUpstreamMessage() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString()))
                    .thenThrow(upstream(UpstreamAuthError.UNAVAILABLE));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals("gotrue said UNAVAILABLE", ex.getMessage());
        }

        @Test
        @DisplayName("A mapped upstream failure does not leak the provider message")
        void mappedErrorUsesDefaultMessage() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString()))
                    .thenThrow(upstream(UpstreamAuthError.INVALID_CREDENTIALS));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertEquals(ErrorCode.INVALID_CREDENTIALS.defaultMessage(), ex.getMessage());
        }

        @Test
        @DisplayName("A rejected login carries no extra details to the client")
        void failedLoginCarriesNoDetails() {
            givenRecentFailures(0);
            when(identityProvider.requestPasswordToken(anyString(), anyString()))
                    .thenThrow(upstream(UpstreamAuthError.INVALID_CREDENTIALS));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.login("ana@example.com", "pwd"));

            assertTrue(ex.details().isEmpty());
        }
    }
}
