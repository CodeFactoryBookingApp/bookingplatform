package com.codefactory.bookingplatform.identity.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
