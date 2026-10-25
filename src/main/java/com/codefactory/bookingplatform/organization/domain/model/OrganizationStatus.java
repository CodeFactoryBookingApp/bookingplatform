package com.codefactory.bookingplatform.organization.domain.model;

/** Lifecycle of a provider (HU-002, HU-003). Only ACTIVE providers are visible and reservable. */
public enum OrganizationStatus {
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    REJECTED
}
