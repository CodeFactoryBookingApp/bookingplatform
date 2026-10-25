package com.codefactory.bookingplatform.catalog.domain.port;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    /** @throws DuplicateNameException if the provider already has a type with that name */
    void saveType(ResourceType type);

    List<ResourceType> findTypes(UUID organizationId);

    Optional<ResourceType> findType(UUID organizationId, UUID typeId);

    /** @throws DuplicateNameException if the location already has a resource with that name */
    void saveNew(Resource resource);

    List<Resource> findAll(UUID organizationId);

    /** Only within the organization: a resource of another provider is not found. */
    Optional<Resource> findById(UUID organizationId, UUID resourceId);

    void addMaintenance(UUID resourceId, MaintenanceWindow window);

    void saveStatus(Resource resource);
}
