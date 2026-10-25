package com.codefactory.bookingplatform.organization.application;

import java.util.UUID;

/** Published when an administrator approves a provider; the email goes out after the commit. */
public record ProviderApproved(UUID organizationId, String name, String contactEmail) {
}
