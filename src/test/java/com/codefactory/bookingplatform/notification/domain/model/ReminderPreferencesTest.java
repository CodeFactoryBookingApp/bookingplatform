package com.codefactory.bookingplatform.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HU-015 / ADR-0010: the quiet period of a recipient. If "now" falls inside it, the reminder waits
 * until it ends; the period may cross midnight. Techniques: boundary values around both ends of the
 * period (start -1 min, start, end -1 min, end), equivalence partitioning (crossing / not crossing
 * midnight, empty period) and the time zone that applies (the recipient's, else the appointment's).
 * Times are in America/Bogota (UTC-5, no daylight saving) unless the test says otherwise.
 */
class ReminderPreferencesTest {

    private static final String BOGOTA = "America/Bogota";

    private static ReminderPreferences quiet(String from, String until, String timezone) {
        return new ReminderPreferences(true, LocalTime.parse(from), LocalTime.parse(until), timezone);
    }

    @ParameterizedTest(name = "[{index}] local {0} -> {1}")
    @CsvSource({
            "2026-11-20T21:59:00-05:00, ",
            "2026-11-20T22:00:00-05:00, 2026-11-21T07:00:00-05:00",
            "2026-11-20T23:30:00-05:00, 2026-11-21T07:00:00-05:00",
            "2026-11-21T00:30:00-05:00, 2026-11-21T07:00:00-05:00",
            "2026-11-21T06:59:00-05:00, 2026-11-21T07:00:00-05:00",
            "2026-11-21T07:00:00-05:00, "
    })
    @DisplayName("HU-015 (BVA): a 22:00-07:00 period crosses midnight; before midnight it ends the next morning, after midnight the same morning")
    void periodCrossingMidnight(String now, String expectedEnd) {
        // Arrange
        ReminderPreferences preferences = quiet("22:00", "07:00", null);

        // Act
        Optional<Instant> end = preferences.quietUntil(OffsetDateTime.parse(now).toInstant(), BOGOTA);

        // Assert
        assertEquals(Optional.ofNullable(expectedEnd).map(t -> OffsetDateTime.parse(t).toInstant()), end);
    }

    @ParameterizedTest(name = "[{index}] local {0} -> {1}")
    @CsvSource({
            "2026-11-21T12:59:00-05:00, ",
            "2026-11-21T13:00:00-05:00, 2026-11-21T15:00:00-05:00",
            "2026-11-21T14:59:00-05:00, 2026-11-21T15:00:00-05:00",
            "2026-11-21T15:00:00-05:00, "
    })
    @DisplayName("HU-015 (BVA): a 13:00-15:00 period includes its start and excludes its end")
    void periodWithinTheDay(String now, String expectedEnd) {
        // Arrange
        ReminderPreferences preferences = quiet("13:00", "15:00", null);

        // Act
        Optional<Instant> end = preferences.quietUntil(OffsetDateTime.parse(now).toInstant(), BOGOTA);

        // Assert
        assertEquals(Optional.ofNullable(expectedEnd).map(t -> OffsetDateTime.parse(t).toInstant()), end);
    }

    @Test
    @DisplayName("HU-015: equal start and end (00:00-00:00) means no quiet period at all")
    void equalBoundsMeanNoQuietPeriod() {
        // Arrange
        ReminderPreferences preferences = quiet("00:00", "00:00", null);

        // Act
        Optional<Instant> end = preferences.quietUntil(Instant.parse("2026-11-21T05:00:00Z"), BOGOTA);

        // Assert
        assertEquals(Optional.empty(), end);
    }

    @Test
    @DisplayName("HU-015: the recipient's own time zone wins over the appointment's")
    void recipientTimezoneWins() {
        // Arrange: 03:00 UTC is 22:00 in Bogota (quiet) but 04:00 in Madrid (quiet until 07:00 Madrid)
        ReminderPreferences preferences = quiet("22:00", "07:00", "Europe/Madrid");

        // Act
        Optional<Instant> end = preferences.quietUntil(Instant.parse("2026-11-21T03:00:00Z"), BOGOTA);

        // Assert
        assertEquals(Optional.of(Instant.parse("2026-11-21T06:00:00Z")), end);
    }

    @Test
    @DisplayName("HU-015: without a time zone of their own, the appointment's time zone applies")
    void appointmentTimezoneByDefault() {
        // Arrange: 03:00 UTC is 22:00 in Bogota, so the period runs until 07:00 Bogota (12:00 UTC)
        ReminderPreferences preferences = quiet("22:00", "07:00", null);

        // Act
        Optional<Instant> end = preferences.quietUntil(Instant.parse("2026-11-21T03:00:00Z"), BOGOTA);

        // Assert
        assertEquals(Optional.of(Instant.parse("2026-11-21T12:00:00Z")), end);
    }

    @Test
    @DisplayName("ADR-0010: when the end time happens twice (clocks go back), the quiet period lasts until the second one")
    void repeatedHourUsesTheLaterOccurrence() {
        // Arrange: New York, 2026-11-01 01:00-02:00 happens twice (EDT then EST)
        ReminderPreferences preferences = quiet("22:00", "01:30", "America/New_York");

        // Act
        Optional<Instant> end = preferences.quietUntil(Instant.parse("2026-11-01T03:00:00Z"), BOGOTA);

        // Assert: 01:30 EST = 06:30 UTC (the earlier 01:30 EDT would be 05:30 UTC)
        assertEquals(Optional.of(Instant.parse("2026-11-01T06:30:00Z")), end);
    }

    @Test
    @DisplayName("HU-015: by default reminders are on with a 22:00-07:00 quiet period in the appointment's time zone")
    void defaultsAreOnWithNightQuietPeriod() {
        // Act
        Optional<Instant> end = ReminderPreferences.DEFAULTS.quietUntil(Instant.parse("2026-11-21T04:00:00Z"), BOGOTA);

        // Assert
        assertEquals(true, ReminderPreferences.DEFAULTS.remindersEnabled());
        assertEquals(Optional.of(Instant.parse("2026-11-21T12:00:00Z")), end);
    }
}
