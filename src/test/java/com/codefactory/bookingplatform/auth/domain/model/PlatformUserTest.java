package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ADR-0009: which accounts count as an active administrator when protecting the last one.
 * Decision table over role x confirmed x deleted x ban, with boundary values on the ban end
 * (one second before now, exactly now, one second after).
 */
class PlatformUserTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private static final Instant CONFIRMED = Instant.parse("2026-01-01T00:00:00Z");

    @ParameterizedTest(name = "[{index}] role={0} confirmed={1} deleted={2} bannedUntil={3} -> {4}")
    @CsvSource({
            "ADMIN,    true,  false,                     , true",
            "PROVIDER, true,  false,                     , false",
            "CLIENT,   true,  false,                     , false",
            "ADMIN,    false, false,                     , false",
            "ADMIN,    true,  true,                      , false",
            "ADMIN,    true,  false, 2026-11-21T15:00:01Z, false",
            "ADMIN,    true,  false, 2026-11-21T15:00:00Z, true",
            "ADMIN,    true,  false, 2026-11-21T14:59:59Z, true"
    })
    @DisplayName("ADR-0009: only a confirmed, not deleted ADMIN whose ban (if any) has ended counts as active")
    void activeAdministratorDecisionTable(AppRole role, boolean confirmed, boolean deleted, Instant bannedUntil,
                                          boolean expected) {
        // Arrange
        PlatformUser user = new PlatformUser(UUID.randomUUID(), role, confirmed ? CONFIRMED : null, bannedUntil,
                deleted ? CONFIRMED.plusSeconds(60) : null);

        // Act
        boolean active = user.isActiveAdministrator(NOW);

        // Assert
        assertEquals(expected, active);
    }
}
