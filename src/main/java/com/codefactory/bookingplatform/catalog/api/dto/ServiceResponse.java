package com.codefactory.bookingplatform.catalog.api.dto;

import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.model.ServiceStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(UUID id, String name, String description, int durationMinutes, int preparationMinutes,
                              int cleanupMinutes, BigDecimal price, String currency, int customerCapacity,
                              boolean group, ServiceStatus status, List<UUID> locationIds,
                              List<RequirementResponse> requirements) {

    public record RequirementResponse(UUID resourceTypeId, int quantity) {

        static RequirementResponse from(ResourceRequirement requirement) {
            return new RequirementResponse(requirement.resourceTypeId(), requirement.quantity());
        }
    }

    public static ServiceResponse from(OfferedService service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getDurationMinutes(), service.getPreparationMinutes(), service.getCleanupMinutes(),
                service.getPrice(), OfferedService.CURRENCY, service.getCustomerCapacity(), service.isGroup(),
                service.getStatus(), service.getLocationIds(),
                service.getRequirements().stream().map(RequirementResponse::from).toList());
    }
}
