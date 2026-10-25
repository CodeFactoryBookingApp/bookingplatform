package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import com.codefactory.bookingplatform.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
public class ResourceEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "resource_type_id", nullable = false, updatable = false)
    private UUID resourceTypeId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResourceStatus status;
}
