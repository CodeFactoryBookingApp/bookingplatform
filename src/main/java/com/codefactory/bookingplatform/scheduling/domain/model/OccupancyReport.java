package com.codefactory.bookingplatform.scheduling.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/** HU-019 "Reporte de ocupación" de una agenda en un rango de fechas. Pure data. */
public record OccupancyReport(UUID agendaId, String agendaName, LocalDate from, LocalDate to, long totalSlots,
                              long occupiedSlots) {

    public double occupancyRate() {
        return totalSlots == 0 ? 0d : (double) occupiedSlots / totalSlots;
    }
}