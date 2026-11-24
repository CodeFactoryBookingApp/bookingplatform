package com.codefactory.bookingplatform.scheduling.api.dto;

import com.codefactory.bookingplatform.scheduling.domain.model.OccupancyReport;

import java.time.LocalDate;
import java.util.UUID;

public record OccupancyResponse(UUID agendaId, String agendaName, LocalDate from, LocalDate to, long totalSlots,
                                long occupiedSlots, double occupancyRate) {

    public static OccupancyResponse from(OccupancyReport report) {
        return new OccupancyResponse(report.agendaId(), report.agendaName(), report.from(), report.to(),
                report.totalSlots(), report.occupiedSlots(), report.occupancyRate());
    }
}