package com.codefactory.bookingplatform.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HU-018 / HU-020: the percentages shown in the booking summary and in the demand report.
 * Equivalence partitioning over the denominator (zero, non-zero) and decision table over the
 * rounding: two decimals, half up, on the exact quotient.
 */
class BookingRatesTest {

    @ParameterizedTest(name = "{0} of {1} = {2} %")
    @CsvSource({
            "1, 4, 25.00",
            "1, 3, 33.33",
            "2, 3, 66.67",
            "1, 8, 12.50",
            "4, 4, 100.00",
            "0, 5, 0.00"
    })
    @DisplayName("HU-020: a rate is numerator over denominator times 100, with two decimals rounded half up")
    void percentageHasTwoDecimalsRoundedHalfUp(long numerator, long denominator, String expected) {
        // Act
        BigDecimal rate = BookingRates.percentage(numerator, denominator);

        // Assert
        assertEquals(new BigDecimal(expected), rate);
    }

    @Test
    @DisplayName("HU-020: a service without bookings has rate 0, not a division by zero")
    void percentageOfNothingIsZero() {
        // Act
        BigDecimal rate = BookingRates.percentage(0, 0);

        // Assert
        assertEquals(0, BigDecimal.ZERO.compareTo(rate));
    }

    @ParameterizedTest(name = "{0} against {1} = {2} %")
    @CsvSource({
            "100, 80, 25.00",
            "4, 1, 300.00",
            "25, 100, -75.00",
            "80, 80, 0.00",
            "0, 3, -100.00",
            "2, 3, -33.33"
    })
    @DisplayName("HU-020: the variation against the previous period is (current - previous) / previous in percent")
    void variationIsRelativeToThePreviousPeriod(String current, String previous, String expected) {
        // Act
        BigDecimal variation = BookingRates.variation(new BigDecimal(current), new BigDecimal(previous));

        // Assert
        assertEquals(new BigDecimal(expected), variation);
    }

    @Test
    @DisplayName("HU-020: with nothing in the previous period there is no variation to report (null, not 0 or infinity)")
    void variationAgainstAnEmptyPeriodIsUndefined() {
        // Act
        BigDecimal variation = BookingRates.variation(new BigDecimal("5"), new BigDecimal("0.00"));

        // Assert
        assertNull(variation);
    }
}
