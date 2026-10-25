package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_maintenances")
@Getter
@Setter
@NoArgsConstructor
public class ResourceMaintenanceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;

    /** Filled by the database default. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
