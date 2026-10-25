package com.codefactory.bookingplatform.catalog.api.dto;

import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request bodies of HU-004. */
public final class ServiceRequests {

    private ServiceRequests() {
    }

    @Schema(description = "What a service is: the fields an update may change too (HU-004)")
    public record ServiceDetails(

            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @Size(max = 500, message = "description must be at most 500 characters")
            String description,

            @NotNull(message = "durationMinutes is required")
            @Min(value = 1, message = "durationMinutes must be at least 1")
            @Max(value = 1440, message = "durationMinutes must be at most 1440")
            Integer durationMinutes,

            @Min(value = 0, message = "preparationMinutes must not be negative")
            @Max(value = 1440, message = "preparationMinutes must be at most 1440")
            @Schema(description = "Defaults to 0")
            Integer preparationMinutes,

            @Min(value = 0, message = "cleanupMinutes must not be negative")
            @Max(value = 1440, message = "cleanupMinutes must be at most 1440")
            @Schema(description = "Defaults to 0")
            Integer cleanupMinutes,

            @NotNull(message = "price is required")
            @DecimalMin(value = "0", message = "price must not be negative")
            @Digits(integer = 10, fraction = 2, message = "price must have at most 10 digits and 2 decimals")
            @Schema(description = "In COP, the only currency of the MVP", example = "35000")
            BigDecimal price,

            @Min(value = 1, message = "customerCapacity must be at least 1")
            @Max(value = 500, message = "customerCapacity must be at most 500")
            @Schema(description = "1 is individual (default), more is a group with that many places")
            Integer customerCapacity) {

        public OfferedService.Details toDomain() {
            return new OfferedService.Details(name, description, durationMinutes,
                    preparationMinutes == null ? 0 : preparationMinutes,
                    cleanupMinutes == null ? 0 : cleanupMinutes,
                    price, customerCapacity == null ? 1 : customerCapacity);
        }
    }

    public record Requirement(

            @NotNull(message = "resourceTypeId is required")
            UUID resourceTypeId,

            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 50, message = "quantity must be at most 50")
            @Schema(description = "Defaults to 1")
            Integer quantity) {

        ResourceRequirement toDomain() {
            return new ResourceRequirement(resourceTypeId, quantity == null ? 1 : quantity);
        }
    }

    @Schema(description = "A new service: its details, where it is provided and what resources it needs (HU-004)")
    public record CreateService(

            @NotNull(message = "details are required")
            @Valid
            ServiceDetails details,

            @NotEmpty(message = "at least one location is required")
            @Size(max = 50, message = "at most 50 locations")
            List<@NotNull(message = "a location id cannot be null") UUID> locationIds,

            @NotEmpty(message = "at least one required resource type is needed")
            @Size(max = 20, message = "at most 20 resource requirements")
            List<@Valid @NotNull(message = "a requirement cannot be null") Requirement> requirements) {

        public List<ResourceRequirement> requirementsToDomain() {
            return requirements.stream().map(Requirement::toDomain).toList();
        }
    }
}
