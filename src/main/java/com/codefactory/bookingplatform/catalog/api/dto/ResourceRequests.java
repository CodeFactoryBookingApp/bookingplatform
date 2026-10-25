package com.codefactory.bookingplatform.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request bodies of HU-009. */
public final class ResourceRequests {

    private ResourceRequests() {
    }

    @Schema(description = "A kind of resource: chair, room, court, professional (HU-009)")
    public record CreateResourceType(

            @NotBlank(message = "name is required")
            @Size(max = 80, message = "name must be at most 80 characters")
            String name,

            @Size(max = 300, message = "description must be at most 300 characters")
            String description) {
    }

    @Schema(description = "A concrete resource of one of the provider's types, in one of its locations (HU-009)")
    public record CreateResource(

            @NotNull(message = "resourceTypeId is required")
            UUID resourceTypeId,

            @NotNull(message = "locationId is required")
            UUID locationId,

            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @NotNull(message = "capacity is required")
            @Min(value = 1, message = "capacity must be at least 1")
            @Max(value = 1000, message = "capacity must be at most 1000")
            @Schema(description = "People the resource serves at once", example = "1")
            Integer capacity) {
    }

    @Schema(description = "A period in which the resource is out of service and offers no slots (HU-009)")
    public record ScheduleMaintenance(

            @NotNull(message = "startsAt is required")
            @Schema(example = "2026-11-03T13:00:00Z")
            Instant startsAt,

            @NotNull(message = "endsAt is required")
            @Schema(example = "2026-11-05T13:00:00Z")
            Instant endsAt,

            @Size(max = 300, message = "reason must be at most 300 characters")
            String reason) {
    }
}
