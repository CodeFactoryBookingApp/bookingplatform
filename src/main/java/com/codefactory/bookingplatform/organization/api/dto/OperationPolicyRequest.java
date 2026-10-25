package com.codefactory.bookingplatform.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "New operating rules of a provider (HU-002); they apply to bookings made from now on")
public record OperationPolicyRequest(

        @NotNull(message = "bookingNoticeMinutesMin is required")
        @PositiveOrZero(message = "bookingNoticeMinutesMin must not be negative")
        Integer bookingNoticeMinutesMin,

        @NotNull(message = "bookingNoticeMinutesMax is required")
        @PositiveOrZero(message = "bookingNoticeMinutesMax must not be negative")
        Integer bookingNoticeMinutesMax,

        @NotNull(message = "freeCancellationWindowMinutes is required")
        @PositiveOrZero(message = "freeCancellationWindowMinutes must not be negative")
        Integer freeCancellationWindowMinutes) {
}
