package com.codefactory.bookingplatform.identity.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-001: self-registration is forbidden for minors.
 *
 * Techniques applied:
 * - Equivalence partitioning: {minor} vs {adult} vs {birth date in the future}.
 * - Boundary value analysis around the 18th birthday: the day before, the very
 *   day, and the day after.
 * - Special-date analysis: 29 February of a leap year, where the anniversary
 *   does not exist in common years.
 */
class AgePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    // --- Boundary value analysis: the exact 18th birthday --------------------

    @ParameterizedTest(name = "born {0} -> adult on 2026-09-18? {1}")
    @DisplayName("The 18th birthday is the exact boundary: before it the client is a minor, from it on an adult")
    @CsvSource({
            // one day before the 18th birthday: still a minor
            "2008-09-19, false",
            // the 18th birthday itself: adult
            "2008-09-18, true",
            // the day after the 18th birthday: adult
            "2008-09-17, true"
    })
    void eighteenthBirthdayIsTheBoundary(LocalDate birthDate, boolean expectedAdult) {
        // Act
        boolean adult = AgePolicy.isAdult(birthDate, TODAY);

        // Assert
        assertEquals(expectedAdult, adult);
    }

    @ParameterizedTest(name = "age {0} -> adult? {1}")
    @DisplayName("Equivalence partitions: ages below 18 are rejected and ages from 18 up are accepted")
    @CsvSource({
            "0, false",
            "17, false",
            "19, true",
            "80, true"
    })
    void equivalencePartitionsByWholeYears(int age, boolean expectedAdult) {
        // Arrange
        LocalDate birthDate = TODAY.minusYears(age);

        // Act
        boolean adult = AgePolicy.isAdult(birthDate, TODAY);

        // Assert
        assertEquals(expectedAdult, adult);
    }

    @Test
    @DisplayName("A birth date in the future is rejected instead of wrapping into a positive age")
    void futureBirthDateIsNotAdult() {
        // Arrange
        LocalDate birthDate = TODAY.plusYears(1);

        // Act
        boolean adult = AgePolicy.isAdult(birthDate, TODAY);

        // Assert
        assertFalse(adult);
    }

    // --- Leap year: 29 February ---------------------------------------------

    @Test
    @DisplayName("Someone born on 29 February is still a minor on 28 February of the common year they turn 18")
    void leapDayBornIsMinorOnFebruaryTwentyEighth() {
        // Arrange
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        LocalDate today = LocalDate.of(2026, 2, 28);

        // Act
        boolean adult = AgePolicy.isAdult(leapBirthDate, today);

        // Assert
        assertFalse(adult);
    }

    @Test
    @DisplayName("Someone born on 29 February becomes an adult on 1 March of the common year they turn 18")
    void leapDayBornIsAdultOnMarchFirst() {
        // Arrange
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        LocalDate today = LocalDate.of(2026, 3, 1);

        // Act
        boolean adult = AgePolicy.isAdult(leapBirthDate, today);

        // Assert
        assertTrue(adult);
    }
}
