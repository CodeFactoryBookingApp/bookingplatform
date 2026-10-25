package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<ResourceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
