package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** resource_types, resources and resource_maintenances (V1, V7). */
@Repository
public class ResourceRepositoryAdapter implements ResourceRepository {

    /** uk_resource_types_org_name and uk_resources_location_name, from V1. */
    private static final String NAME_CONSTRAINT = "_name";

    private final ResourceTypeJpaRepository types;
    private final ResourceJpaRepository resources;
    private final ResourceMaintenanceJpaRepository maintenances;

    public ResourceRepositoryAdapter(ResourceTypeJpaRepository types, ResourceJpaRepository resources,
                                     ResourceMaintenanceJpaRepository maintenances) {
        this.types = types;
        this.resources = resources;
        this.maintenances = maintenances;
    }

    @Override
    public void saveType(ResourceType type) {
        ResourceTypeEntity entity = new ResourceTypeEntity();
        entity.setId(type.id());
        entity.setOrganizationId(type.organizationId());
        entity.setName(type.name());
        entity.setDescription(type.description());
        try {
            types.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    @Override
    public List<ResourceType> findTypes(UUID organizationId) {
        return types.findByOrganizationIdOrderByName(organizationId).stream()
                .map(ResourceRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ResourceType> findType(UUID organizationId, UUID typeId) {
        if (typeId == null) {
            return Optional.empty();
        }
        return types.findByIdAndOrganizationId(typeId, organizationId).map(ResourceRepositoryAdapter::toDomain);
    }

    @Override
    public void saveNew(Resource resource) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(resource.getId());
        entity.setOrganizationId(resource.getOrganizationId());
        entity.setResourceTypeId(resource.getResourceTypeId());
        entity.setLocationId(resource.getLocationId());
        entity.setName(resource.getName());
        entity.setCapacity(resource.getCapacity());
        entity.setStatus(resource.getStatus());
        try {
            resources.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    @Override
    public List<Resource> findAll(UUID organizationId) {
        List<ResourceEntity> entities = resources.findByOrganizationIdOrderByName(organizationId);
        Map<UUID, List<MaintenanceWindow>> windows = maintenancesOf(entities.stream().map(ResourceEntity::getId).toList());
        return entities.stream()
                .map(entity -> toDomain(entity, windows.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    public Optional<Resource> findById(UUID organizationId, UUID resourceId) {
        return resources.findByIdAndOrganizationId(resourceId, organizationId)
                .map(entity -> toDomain(entity, maintenancesOf(List.of(entity.getId()))
                        .getOrDefault(entity.getId(), List.of())));
    }

    @Override
    public void addMaintenance(UUID resourceId, MaintenanceWindow window) {
        ResourceMaintenanceEntity entity = new ResourceMaintenanceEntity();
        entity.setId(window.id());
        entity.setResourceId(resourceId);
        entity.setStartsAt(window.startsAt());
        entity.setEndsAt(window.endsAt());
        entity.setReason(window.reason());
        maintenances.saveAndFlush(entity);
    }

    @Override
    public void saveStatus(Resource resource) {
        ResourceEntity entity = resources.findById(resource.getId()).orElseThrow();
        entity.setStatus(resource.getStatus());
        resources.saveAndFlush(entity);
    }

    @Override
    public long countActive(UUID locationId, UUID resourceTypeId) {
        return resources.countByLocationIdAndResourceTypeIdAndStatus(locationId, resourceTypeId, ResourceStatus.ACTIVE);
    }

    private Map<UUID, List<MaintenanceWindow>> maintenancesOf(List<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return maintenances.findByResourceIdInOrderByStartsAt(resourceIds).stream()
                .collect(Collectors.groupingBy(ResourceMaintenanceEntity::getResourceId,
                        Collectors.mapping(window -> new MaintenanceWindow(window.getId(), window.getStartsAt(),
                                window.getEndsAt(), window.getReason()), Collectors.toList())));
    }

    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
        if (constraint != null && constraint.endsWith(NAME_CONSTRAINT)) {
            return new DuplicateNameException(ex);
        }
        return ex;
    }

    private static ResourceType toDomain(ResourceTypeEntity entity) {
        return new ResourceType(entity.getId(), entity.getOrganizationId(), entity.getName(), entity.getDescription());
    }

    private static Resource toDomain(ResourceEntity entity, List<MaintenanceWindow> windows) {
        return new Resource(entity.getId(), entity.getOrganizationId(), entity.getResourceTypeId(),
                entity.getLocationId(), entity.getName(), entity.getCapacity(), entity.getStatus(), windows);
    }
}
