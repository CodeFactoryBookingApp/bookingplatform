package com.codefactory.bookingplatform.catalog.domain.model;

/** A service is offered only while ACTIVE; deactivating keeps it and its bookings (HU-004). */
public enum ServiceStatus {
    ACTIVE,
    INACTIVE
}
