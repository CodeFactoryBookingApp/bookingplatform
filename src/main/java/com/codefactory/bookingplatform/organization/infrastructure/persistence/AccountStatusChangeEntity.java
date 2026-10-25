package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A status change of an organization with its reason (HU-003). The table also holds client
 * changes; this module only ever writes ORGANIZATION rows.
 */
@Entity
@Table(name = "account_status_changes")
@Getter
@Setter
@NoArgsConstructor
public class AccountStatusChangeEntity {

    static final String ORGANIZATION = "ORGANIZATION";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "affected_bookings", nullable = false)
    private int affectedBookings;

    @Column(name = "changed_by")
    private UUID changedBy;

    /** Filled by the database default: the same now() the status trigger compares against. */
    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;
}
