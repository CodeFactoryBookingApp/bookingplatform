package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy.DefaultClosureStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One version of the operating rules of a provider (organization_policies, HU-002). */
@Entity
@Table(name = "organization_policies")
@Getter
@Setter
@NoArgsConstructor
public class OperationPolicyEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "booking_notice_minutes_min", nullable = false, updatable = false)
    private int bookingNoticeMinutesMin;

    @Column(name = "booking_notice_minutes_max", nullable = false, updatable = false)
    private int bookingNoticeMinutesMax;

    @Column(name = "free_cancellation_window_minutes", nullable = false, updatable = false)
    private int freeCancellationWindowMinutes;

    @Column(name = "manual_confirmation", nullable = false, updatable = false)
    private boolean manualConfirmation;

    @Column(name = "max_reschedules", nullable = false, updatable = false)
    private int maxReschedules;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_closure_status", nullable = false, updatable = false, length = 20)
    private DefaultClosureStatus defaultClosureStatus;

    @Column(name = "auto_close_delay_hours", nullable = false, updatable = false)
    private int autoCloseDelayHours;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "replaced_at")
    private Instant replacedAt;
}
