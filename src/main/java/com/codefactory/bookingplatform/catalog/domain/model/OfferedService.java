package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * A service a provider offers (HU-004). Pure domain model. Named OfferedService so it does not
 * clash with Spring's {@code @Service}.
 *
 * <p>It is born INACTIVE and is offered only once activated, which needs enough active resources
 * of every required type in every location where it is provided (HU-009). Prices are in COP, the
 * only currency of the MVP.
 */
@Getter
public class OfferedService {

    public static final String CURRENCY = "COP";

    private final UUID id;
    private final UUID organizationId;
    private String name;
    private String description;
    private int durationMinutes;
    private int preparationMinutes;
    private int cleanupMinutes;
    private BigDecimal price;
    private int customerCapacity;
    private ServiceStatus status;
    private final List<UUID> locationIds;
    private final List<ResourceRequirement> requirements;

    /** The fields an update may change; locations and requirements are fixed when the service is created. */
    public record Details(String name, String description, int durationMinutes, int preparationMinutes,
                          int cleanupMinutes, BigDecimal price, int customerCapacity) {
    }

    @SuppressWarnings("java:S107") // reconstitution from storage needs every field
    public OfferedService(UUID id, UUID organizationId, Details details, ServiceStatus status, List<UUID> locationIds,
                          List<ResourceRequirement> requirements) {
        this.id = id;
        this.organizationId = organizationId;
        apply(details);
        this.status = status;
        this.locationIds = List.copyOf(locationIds);
        this.requirements = List.copyOf(requirements);
    }

    /**
     * HU-004 "Crear un servicio con recurso requerido": at least one location and one required
     * resource type; it stays INACTIVE, available to be activated.
     */
    public static OfferedService create(UUID id, UUID organizationId, Details details, List<UUID> locationIds,
                                        List<ResourceRequirement> requirements) {
        validate(details);
        Map<String, String> errors = new LinkedHashMap<>();
        if (locationIds == null || locationIds.isEmpty()) {
            errors.put("locationIds", "at least one location is required");
        } else if (new HashSet<>(locationIds).size() != locationIds.size()) {
            errors.put("locationIds", "a location is listed twice");
        }
        if (requirements == null || requirements.isEmpty()) {
            errors.put("requirements", "at least one required resource type is needed");
        } else if (requirements.stream().map(ResourceRequirement::resourceTypeId).distinct().count()
                != requirements.size()) {
            errors.put("requirements", "a resource type is listed twice");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid service", errors);
        }
        return new OfferedService(id, organizationId, details, ServiceStatus.INACTIVE, locationIds, requirements);
    }

    private static void validate(Details details) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (details.durationMinutes() < 1) {
            errors.put("durationMinutes", "must be at least 1");
        }
        if (details.preparationMinutes() < 0) {
            errors.put("preparationMinutes", "must not be negative");
        }
        if (details.cleanupMinutes() < 0) {
            errors.put("cleanupMinutes", "must not be negative");
        }
        if (details.price() == null || details.price().signum() < 0 || details.price().scale() > 2) {
            errors.put("price", "must be zero or more, with at most two decimals");
        }
        if (details.customerCapacity() < 1) {
            errors.put("customerCapacity", "must be at least 1: 1 is individual, more is a group");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid service", errors);
        }
    }

    private void apply(Details details) {
        this.name = details.name();
        this.description = details.description();
        this.durationMinutes = details.durationMinutes();
        this.preparationMinutes = details.preparationMinutes();
        this.cleanupMinutes = details.cleanupMinutes();
        this.price = details.price();
        this.customerCapacity = details.customerCapacity();
    }

    public Details details() {
        return new Details(name, description, durationMinutes, preparationMinutes, cleanupMinutes, price,
                customerCapacity);
    }

    /** HU-004 "Servicio grupal con cupo válido": more than one client per session. */
    public boolean isGroup() {
        return customerCapacity > 1;
    }

    /**
     * HU-004 "Cambio de duración no afecta reservas existentes": the new values apply to bookings
     * made from now on. Existing ones keep their times, which they store themselves.
     */
    public void update(Details details) {
        validate(details);
        apply(details);
    }

    /**
     * HU-009 "Activación de servicio sin recursos suficientes". {@code activeResources} answers
     * how many ACTIVE resources of a type a location has. Every location must cover every
     * requirement; each shortfall is reported as "location/type".
     */
    public void activate(BiFunction<UUID, UUID, Long> activeResources) {
        if (status == ServiceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "The service is already active");
        }
        Map<String, String> shortfalls = new LinkedHashMap<>();
        for (UUID locationId : locationIds) {
            for (ResourceRequirement requirement : requirements) {
                long available = activeResources.apply(locationId, requirement.resourceTypeId());
                if (available < requirement.quantity()) {
                    shortfalls.put(locationId + "/" + requirement.resourceTypeId(),
                            "requires " + requirement.quantity() + ", has " + available);
                }
            }
        }
        if (!shortfalls.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RESOURCES,
                    "Some locations lack the active resources the service requires", shortfalls);
        }
        this.status = ServiceStatus.ACTIVE;
    }

    /** HU-004: an inactive service is kept, with its bookings, but no longer offered. */
    public void deactivate() {
        if (status == ServiceStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "The service is already inactive");
        }
        this.status = ServiceStatus.INACTIVE;
    }
}
