package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A concrete resource of a provider: a chair, a room, a court (HU-009). Pure domain model.
 *
 * <p>It belongs to one location and has a capacity: how many people it serves at once. It is
 * either ACTIVE or RETIRED; being in maintenance is a period, so availability depends on the time
 * asked about.
 */
@Getter
public class Resource {

    private final UUID id;
    private final UUID organizationId;
    private final UUID resourceTypeId;
    private final UUID locationId;
    private final String name;
    private final int capacity;
    private ResourceStatus status;
    private final List<MaintenanceWindow> maintenances;

    @SuppressWarnings("java:S107") // reconstitution from storage needs every field
    public Resource(UUID id, UUID organizationId, UUID resourceTypeId, UUID locationId, String name, int capacity,
                    ResourceStatus status, List<MaintenanceWindow> maintenances) {
        this.id = id;
        this.organizationId = organizationId;
        this.resourceTypeId = resourceTypeId;
        this.locationId = locationId;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
        this.maintenances = new ArrayList<>(maintenances);
    }

    /** HU-009: a new instance of a resource type in one location, available right away. */
    public static Resource create(UUID id, UUID organizationId, UUID resourceTypeId, UUID locationId, String name,
                                  int capacity) {
        if (capacity < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid capacity",
                    Map.of("capacity", "must be at least 1"));
        }
        return new Resource(id, organizationId, resourceTypeId, locationId, name, capacity, ResourceStatus.ACTIVE,
                List.of());
    }

    public List<MaintenanceWindow> getMaintenances() {
        return List.copyOf(maintenances);
    }

    /** HU-009: the resource goes out of service for a period; two periods of one resource never overlap. */
    public MaintenanceWindow scheduleMaintenance(MaintenanceWindow window) {
        if (status == ResourceStatus.RETIRED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A retired resource cannot be scheduled for maintenance");
        }
        if (maintenances.stream().anyMatch(existing -> existing.overlaps(window.startsAt(), window.endsAt()))) {
            throw new BusinessException(ErrorCode.OVERLAPPING_PERIOD,
                    "The maintenance overlaps another one of the same resource");
        }
        maintenances.add(window);
        return window;
    }

    /**
     * HU-009 "No se puede retirar un recurso con reservas futuras confirmadas": retiring is
     * refused while bookings are pending on it, and the error says how many.
     */
    public void retire(long futureBookings) {
        if (status == ResourceStatus.RETIRED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "The resource is already retired");
        }
        if (futureBookings > 0) {
            throw new BusinessException(ErrorCode.HAS_FUTURE_BOOKINGS,
                    "The resource has future bookings; resolve them before retiring it",
                    Map.of("futureBookings", String.valueOf(futureBookings)));
        }
        this.status = ResourceStatus.RETIRED;
    }

    /**
     * HU-009 "Recurso en mantenimiento no genera disponibilidad": whether the resource can serve
     * during [from, to). The availability engine (HU-008) asks this for every slot.
     */
    public boolean isAvailableDuring(Instant from, Instant to) {
        return status == ResourceStatus.ACTIVE
                && maintenances.stream().noneMatch(window -> window.overlaps(from, to));
    }

    public ResourceCondition conditionAt(Instant at) {
        if (status == ResourceStatus.RETIRED) {
            return ResourceCondition.RETIRED;
        }
        return maintenances.stream().anyMatch(window -> window.covers(at))
                ? ResourceCondition.IN_MAINTENANCE
                : ResourceCondition.AVAILABLE;
    }
}
