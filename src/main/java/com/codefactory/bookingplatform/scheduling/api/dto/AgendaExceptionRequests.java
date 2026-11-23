package com.codefactory.bookingplatform.scheduling.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Request bodies of HU-007. */
public final class AgendaExceptionRequests {

    private AgendaExceptionRequests() {
    }

    @Schema(description = "Marks the agenda fully closed on that date (HU-007)")
    public record CloseDate(

            @NotNull(message = "date is required")
            LocalDate date,

            @Size(max = 300, message = "reason must be at most 300 characters")
            String reason) {
    }

    @Schema(description = "Overrides the weekly blocks of the agenda on that date with a special schedule (HU-007)")
    public record SpecialHours(

            @NotNull(message = "date is required")
            LocalDate date,

            @NotNull(message = "startTime is required")
            @Schema(type = "string", example = "09:00")
            LocalTime startTime,

            @NotNull(message = "endTime is required")
            @Schema(type = "string", example = "13:00")
            LocalTime endTime,

            @Size(max = 300, message = "reason must be at most 300 characters")
            String reason) {
    }
}