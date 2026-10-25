package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationCategoryJpaRepository extends JpaRepository<OrganizationCategoryEntity, UUID> {
}
