package com.codefactory.bookingplatform.catalog.domain.model;

/** Stored status of a resource. Maintenance is a period, not a status: see {@link MaintenanceWindow}. */
public enum ResourceStatus {
    ACTIVE,
    RETIRED
}
