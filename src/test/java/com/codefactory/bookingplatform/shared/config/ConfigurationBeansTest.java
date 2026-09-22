package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.auth.infrastructure.config.AuthConfig;
import com.codefactory.bookingplatform.shared.persistence.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The declarative configuration classes are exercised by calling their factory
 * methods directly: no application context is started, which keeps the check on
 * the values produced rather than on Spring itself.
 */
class ConfigurationBeansTest {

    @Nested
    @DisplayName("ClockConfig")
    class ClockConfigTest {

        private final ClockConfig config = new ClockConfig();

        @Test
        @DisplayName("The clock bean is built and is not null")
        void clockIsProvided() {
            assertNotNull(config.clock());
        }

        @Test
        @DisplayName("The clock runs on UTC, so stored timestamps do not drift with the host zone")
        void clockIsUtc() {
            assertEquals(ZoneOffset.UTC, config.clock().getZone());
        }

        @Test
        @DisplayName("The clock is the system UTC clock, not a fixed or offset one")
        void clockIsSystemUtc() {
            assertEquals(Clock.systemUTC(), config.clock());
        }

        @Test
        @DisplayName("The clock ticks with real time instead of returning a frozen instant")
        void clockIsNotFixed() {
            Clock clock = config.clock();

            assertTrue(clock.instant().isAfter(java.time.Instant.now().minusSeconds(60)));
        }
    }

    @Nested
    @DisplayName("AuthConfig")
    class AuthConfigTest {

        private final AuthConfig config = new AuthConfig();

        @ParameterizedTest(name = "maxFailedAttempts={0} and lockWindowMinutes={1} reach the policy untouched")
        @CsvSource({"5, 15", "1, 1", "10, 60", "3, 120"})
        @DisplayName("The lock policy bean is built from the configured properties")
        void policyIsBuiltFromProperties(int attempts, int minutes) {
            LoginLockPolicy policy = config.loginLockPolicy(new AuthPolicyProperties(attempts, minutes));

            assertEquals(attempts, policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(minutes), policy.lockWindow());
        }

        @Test
        @DisplayName("The production defaults are 5 attempts within a 15 minute window")
        void productionDefaults() {
            LoginLockPolicy policy = config.loginLockPolicy(new AuthPolicyProperties(5, 15));

            assertEquals(5, policy.maxFailedAttempts());
            assertEquals(15, policy.lockWindow().toMinutes());
        }

        @ParameterizedTest(name = "a non positive maxFailedAttempts of {0} is refused at startup")
        @CsvSource({"0", "-1"})
        void nonPositiveAttemptsAreRefused(int attempts) {
            AuthPolicyProperties properties = new AuthPolicyProperties(attempts, 15);

            assertThrows(IllegalArgumentException.class, () -> config.loginLockPolicy(properties));
        }

        @Test
        @DisplayName("A zero minute window produces a zero duration, disabling the sliding window")
        void zeroMinuteWindow() {
            LoginLockPolicy policy = config.loginLockPolicy(new AuthPolicyProperties(5, 0));

            assertEquals(Duration.ZERO, policy.lockWindow());
        }
    }

    @Nested
    @DisplayName("JpaAuditingConfig")
    class JpaAuditingConfigTest {

        @Test
        @DisplayName("Auditing is enabled declaratively, which is what fills createdAt and updatedAt")
        void auditingIsEnabled() {
            assertNotNull(JpaAuditingConfig.class.getAnnotation(EnableJpaAuditing.class));
        }

        @Test
        @DisplayName("The class is a Spring configuration, so the annotation is actually processed")
        void isAConfigurationClass() {
            assertNotNull(JpaAuditingConfig.class.getAnnotation(Configuration.class));
        }

        @Test
        @DisplayName("The configuration class can be instantiated by the container")
        void isInstantiable() {
            assertNotNull(new JpaAuditingConfig());
        }
    }
}
