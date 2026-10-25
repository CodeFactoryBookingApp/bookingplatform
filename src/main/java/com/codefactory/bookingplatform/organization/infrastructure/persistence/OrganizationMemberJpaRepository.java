package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberJpaRepository extends JpaRepository<OrganizationMemberEntity, UUID> {

    Optional<OrganizationMemberEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<OrganizationMemberEntity> findByUserId(UUID userId);
}
