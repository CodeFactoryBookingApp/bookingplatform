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

    @Test
    @DisplayName("Exactly 18 years old is accepted")
    void exactlyEighteenIsAdult() {
        assertTrue(AgePolicy.isAdult(TODAY.minusYears(18), TODAY));
    }

    @Test
    @DisplayName("One day short of 18 is rejected")
    void oneDayShortOfEighteenIsMinor() {
        assertFalse(AgePolicy.isAdult(TODAY.minusYears(18).plusDays(1), TODAY));
    }

    @Test
    @DisplayName("Adults well over 18 are accepted")
    void olderAdultIsAdult() {
        assertTrue(AgePolicy.isAdult(TODAY.minusYears(35), TODAY));
    }

    // --- Boundary value analysis: the exact 18th birthday --------------------

    @Test
    @DisplayName("The day after turning 18 is accepted")
    void oneDayAfterEighteenthBirthdayIsAdult() {
        assertTrue(AgePolicy.isAdult(TODAY.minusYears(18).minusDays(1), TODAY));
    }

    @ParameterizedTest(name = "born {0} -> adult on 2026-09-18? {1}")
    @DisplayName("The 18th birthday is the exact boundary: before it the client is a minor, from it on an adult")
    @CsvSource({
            // one year before the boundary: clearly a minor
            "2009-09-18, false",
            // one day before the 18th birthday: still a minor
            "2008-09-19, false",
            // the 18th birthday itself: adult
            "2008-09-18, true",
            // the day after the 18th birthday: adult
            "2008-09-17, true",
            // one year past the boundary: clearly an adult
            "2007-09-18, true"
    })
    void eighteenthBirthdayIsTheBoundary(LocalDate birthDate, boolean expectedAdult) {
        assertEquals(expectedAdult, AgePolicy.isAdult(birthDate, TODAY));
    }

    @ParameterizedTest(name = "age {0} -> adult? {1}")
    @DisplayName("Equivalence partitions: ages below 18 are rejected and ages from 18 up are accepted")
    @CsvSource({
            "0, false",
            "1, false",
            "17, false",
            "18, true",
            "19, true",
            "80, true"
    })
    void equivalencePartitionsByWholeYears(int age, boolean expectedAdult) {
        assertEquals(expectedAdult, AgePolicy.isAdult(TODAY.minusYears(age), TODAY));
    }

    @Test
    @DisplayName("A newborn registering the same day is a minor")
    void bornTodayIsMinor() {
        assertFalse(AgePolicy.isAdult(TODAY, TODAY));
    }

    @Test
    @DisplayName("A birth date in the future is rejected instead of wrapping into a positive age")
    void futureBirthDateIsNotAdult() {
        assertFalse(AgePolicy.isAdult(TODAY.plusYears(1), TODAY));
    }

    // --- Leap year: 29 February ---------------------------------------------

    @Test
    @DisplayName("Someone born on 29 February is still a minor on 28 February of the common year they turn 18")
    void leapDayBornIsMinorOnFebruaryTwentyEighth() {
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        assertFalse(AgePolicy.isAdult(leapBirthDate, LocalDate.of(2026, 2, 28)));
    }

    @Test
    @DisplayName("Someone born on 29 February becomes an adult on 1 March of the common year they turn 18")
    void leapDayBornIsAdultOnMarchFirst() {
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        assertTrue(AgePolicy.isAdult(leapBirthDate, LocalDate.of(2026, 3, 1)));
    }

    @Test
    @DisplayName("Someone born on 29 February is an adult on 29 February of a later leap year")
    void leapDayBornIsAdultOnTheNextLeapAnniversary() {
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        assertTrue(AgePolicy.isAdult(leapBirthDate, LocalDate.of(2028, 2, 29)));
    }

    @Test
    @DisplayName("Someone born on 29 February is a minor on 28 February of the leap year before turning 18")
    void leapDayBornIsMinorTheDayBeforeTheLeapAnniversary() {
        LocalDate leapBirthDate = LocalDate.of(2008, 2, 29);
        assertFalse(AgePolicy.isAdult(leapBirthDate, LocalDate.of(2026, 2, 27)));
    }

    @Test
    @DisplayName("The minimum legal age published by the policy is 18")
    void minimumAgeIsEighteen() {
        assertEquals(18, AgePolicy.MINIMUM_AGE);
    }
}
