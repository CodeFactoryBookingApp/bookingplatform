package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaJpaRepository extends JpaRepository<AgendaEntity, UUID> {

    List<AgendaEntity> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<AgendaEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
