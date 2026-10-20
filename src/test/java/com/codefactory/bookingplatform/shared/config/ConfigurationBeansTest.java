package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.auth.infrastructure.config.AuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The declarative configuration classes are exercised by calling their factory
 * methods directly: no application context is started, which keeps the check on
 * the values produced rather than on Spring itself.
 */
class ConfigurationBeansTest {

    @Nested
    @DisplayName("AuthConfig")
    class AuthConfigTest {

        private final AuthConfig config = new AuthConfig();

        @ParameterizedTest(name = "maxFailedAttempts={0} and lockWindowMinutes={1} reach the policy untouched")
        @CsvSource({"5, 15"})
        @DisplayName("The lock policy bean is built from the configured properties, reading the window in minutes")
        void policyIsBuiltFromProperties(int attempts, int minutes) {
            // Arrange
            AuthPolicyProperties properties = new AuthPolicyProperties(attempts, minutes);

            // Act
            LoginLockPolicy policy = config.loginLockPolicy(properties);

            // Assert
            assertEquals(attempts, policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(minutes), policy.lockWindow());
        }
    }
}
