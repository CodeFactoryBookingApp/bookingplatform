package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, UUID> {

    List<ServiceEntity> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<ServiceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
