package com.codefactory.bookingplatform.identity.domain.service;

import java.time.LocalDate;
import java.time.Period;

/**
 * Business rule (HU-001): self-registration is forbidden for minors.
 */
public final class AgePolicy {

    public static final int MINIMUM_AGE = 18;

    private AgePolicy() {
    }

    public static boolean isAdult(LocalDate birthDate, LocalDate onDate) {
        return Period.between(birthDate, onDate).getYears() >= MINIMUM_AGE;
    }
}
