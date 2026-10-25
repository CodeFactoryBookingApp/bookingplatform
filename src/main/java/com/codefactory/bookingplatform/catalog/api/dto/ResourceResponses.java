package com.codefactory.bookingplatform.catalog.api.dto;

import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceCondition;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response bodies of HU-009. */
public final class ResourceResponses {

    private ResourceResponses() {
    }

    public record ResourceTypeResponse(UUID id, String name, String description) {

        public static ResourceTypeResponse from(ResourceType type) {
            return new ResourceTypeResponse(type.id(), type.name(), type.description());
        }
    }

    public record MaintenanceResponse(UUID id, Instant startsAt, Instant endsAt, String reason) {

        static MaintenanceResponse from(MaintenanceWindow window) {
            return new MaintenanceResponse(window.id(), window.startsAt(), window.endsAt(), window.reason());
        }
    }

    /** {@code condition} is the state right now: AVAILABLE, IN_MAINTENANCE or RETIRED. */
    public record ResourceResponse(UUID id, UUID resourceTypeId, UUID locationId, String name, int capacity,
                                   ResourceCondition condition, List<MaintenanceResponse> maintenances) {

        public static ResourceResponse from(Resource resource, Instant now) {
            return new ResourceResponse(resource.getId(), resource.getResourceTypeId(), resource.getLocationId(),
                    resource.getName(), resource.getCapacity(), resource.conditionAt(now),
                    resource.getMaintenances().stream().map(MaintenanceResponse::from).toList());
        }
    }
}
