package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceTypeJpaRepository extends JpaRepository<ResourceTypeEntity, UUID> {

    List<ResourceTypeEntity> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<ResourceTypeEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
