package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OperationPolicyJpaRepository extends JpaRepository<OperationPolicyEntity, UUID> {

    Optional<OperationPolicyEntity> findByOrganizationIdAndReplacedAtIsNull(UUID organizationId);

    /**
     * Runs at once, before the new version is inserted: there is a unique index on the current
     * version of each organization, and Hibernate flushes inserts before updates.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OperationPolicyEntity p set p.replacedAt = :at "
            + "where p.organizationId = :organizationId and p.replacedAt is null")
    int closeCurrent(@Param("organizationId") UUID organizationId, @Param("at") Instant at);
}
