package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocationJpaRepository extends JpaRepository<LocationEntity, UUID> {

    List<LocationEntity> findByOrganizationId(UUID organizationId, Sort sort);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
