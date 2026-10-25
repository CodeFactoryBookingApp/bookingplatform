package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<ResourceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByLocationIdAndResourceTypeIdAndStatus(UUID locationId, UUID resourceTypeId, ResourceStatus status);
}
