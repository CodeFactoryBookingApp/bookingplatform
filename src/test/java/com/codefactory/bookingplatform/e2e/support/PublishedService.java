package com.codefactory.bookingplatform.e2e.support;

/** A service an approved provider has activated and scheduled, ready to be found and booked. */
public record PublishedService(Session provider, String organizationId, String locationId, String serviceId,
                               String serviceName) {
}
