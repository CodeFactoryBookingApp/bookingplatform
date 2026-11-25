package com.codefactory.bookingplatform.scheduling.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HU-019 "Reporte de ocupación": the occupancy rate of an agenda is the fraction of the slots it
 * offered that ended up booked.
 *
 * <p>Technique: equivalence partitioning with boundary values over (offered, booked): no slot
 * offered at all (a closed period must read 0, not divide by zero), none booked, part booked and
 * every slot booked.
 */
class OccupancyReportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);

    @ParameterizedTest(name = "[{index}] {1} booked of {0} offered -> {2}")
    @CsvSource({
            "0, 0, 0.0",
            "4, 0, 0.0",
            "4, 1, 0.25",
            "3, 2, 0.6666666666666666",
            "4, 4, 1.0"
    })
    @DisplayName("HU-019: the occupancy rate is booked over offered slots, and 0 when nothing was offered")
    void occupancyRateIsBookedOverOffered(long offered, long booked, double expected) {
        // Arrange
        OccupancyReport report = new OccupancyReport(UUID.randomUUID(), "Agenda", FROM, FROM.plusDays(6), offered,
                booked);

        // Act
        double rate = report.occupancyRate();

        // Assert
        assertEquals(expected, rate);
    }
}
