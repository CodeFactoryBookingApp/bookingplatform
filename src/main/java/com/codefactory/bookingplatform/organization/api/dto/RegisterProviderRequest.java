package com.codefactory.bookingplatform.organization.api.dto;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Provider registration payload (HU-002): the account, the business, its locations and its operating rules")
public record RegisterProviderRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 160, message = "email must be at most 160 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "name is required")
        @Size(max = 160, message = "name must be at most 160 characters")
        String name,

        @NotNull(message = "categoryId is required")
        UUID categoryId,

        @NotNull(message = "identificationType is required")
        IdentificationType identificationType,

        @NotBlank(message = "identificationNumber is required")
        @Pattern(regexp = "^(?=.*[A-Za-z0-9])[A-Za-z0-9-]{5,20}$",
                message = "identificationNumber must be 5-20 letters, digits or hyphens")
        String identificationNumber,

        @Email(message = "contactEmail must be a valid address")
        @Size(max = 160, message = "contactEmail must be at most 160 characters")
        @Schema(description = "Defaults to the account email")
        String contactEmail,

        @NotBlank(message = "contactPhone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$",
                message = "contactPhone must contain 7-15 digits, optionally prefixed with +")
        String contactPhone,

        @Size(max = 60, message = "timezone must be at most 60 characters")
        @Schema(description = "IANA time zone; defaults to America/Bogota", example = "America/Bogota")
        String timezone,

        @NotEmpty(message = "at least one location is required")
        @Size(max = 20, message = "at most 20 locations can be registered at once")
        List<@Valid @NotNull(message = "a location cannot be null") LocationRequest> locations,

        @NotNull(message = "bookingNoticeMinutesMin is required")
        @PositiveOrZero(message = "bookingNoticeMinutesMin must not be negative")
        Integer bookingNoticeMinutesMin,

        @NotNull(message = "bookingNoticeMinutesMax is required")
        @PositiveOrZero(message = "bookingNoticeMinutesMax must not be negative")
        Integer bookingNoticeMinutesMax,

        @NotNull(message = "freeCancellationWindowMinutes is required")
        @PositiveOrZero(message = "freeCancellationWindowMinutes must not be negative")
        Integer freeCancellationWindowMinutes) {

    public record LocationRequest(

            @NotBlank(message = "location name is required")
            @Size(max = 120, message = "location name must be at most 120 characters")
            String name,

            @NotBlank(message = "location address is required")
            @Size(max = 200, message = "location address must be at most 200 characters")
            String address,

            @NotNull(message = "location cityId is required")
            UUID cityId,

            @Size(max = 60, message = "location timezone must be at most 60 characters")
            @Schema(description = "IANA time zone; empty means the one of the provider")
            String timezone) {
    }
}
