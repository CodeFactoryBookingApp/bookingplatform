package com.codefactory.bookingplatform.scheduling.api.dto;

import com.codefactory.bookingplatform.scheduling.application.GetAvailabilityUseCase.DayAvailability;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailableSlot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(LocalDate date, List<SlotResponse> slots) {

    public record SlotResponse(Instant startsAt, Instant endsAt) {

        static SlotResponse from(AvailableSlot slot) {
            return new SlotResponse(slot.startsAt(), slot.endsAt());
        }
    }

    public static AvailabilityResponse from(DayAvailability day) {
        return new AvailabilityResponse(day.date(), day.slots().stream().map(SlotResponse::from).toList());
    }
}