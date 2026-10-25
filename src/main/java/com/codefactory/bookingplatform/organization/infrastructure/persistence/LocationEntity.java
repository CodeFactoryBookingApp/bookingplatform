package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class LocationEntity extends AuditableEntity {

    static final String ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "city_id", nullable = false)
    private UUID cityId;

    @Column(name = "timezone", length = 60)
    private String timezone;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
