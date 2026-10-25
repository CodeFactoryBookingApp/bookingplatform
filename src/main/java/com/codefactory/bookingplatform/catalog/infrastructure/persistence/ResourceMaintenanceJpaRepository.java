package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceMaintenanceJpaRepository extends JpaRepository<ResourceMaintenanceEntity, UUID> {

    List<ResourceMaintenanceEntity> findByResourceIdInOrderByStartsAt(Collection<UUID> resourceIds);
}
