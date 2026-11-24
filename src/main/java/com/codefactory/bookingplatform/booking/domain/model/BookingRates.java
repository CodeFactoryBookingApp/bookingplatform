package com.codefactory.bookingplatform.booking.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BookingRates {

    private BookingRates() {
    }

    public static BigDecimal percentage(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO : rate(BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    public static BigDecimal variation(BigDecimal current, BigDecimal previous) {
        return previous.signum() == 0 ? null : rate(current.subtract(previous), previous);
    }

    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        return numerator.movePointRight(2).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
