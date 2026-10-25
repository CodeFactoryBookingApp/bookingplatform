package com.codefactory.bookingplatform.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Providers written straight into PostgreSQL for the integration tests of the modules that sit on
 * top of organization (catalog, scheduling). HU-002 itself is tested through its API in
 * ProviderOnboardingIT; here a provider is only the starting point.
 *
 * <p>Each call creates new rows with unique names, so tests sharing the container never collide.
 */
public final class ProviderFixture {

    /** An ACTIVE provider with one location, policy version 1 and its OWNER. */
    public record Provider(UUID organizationId, UUID ownerId, String ownerEmail, UUID locationId, UUID policyId) {
    }

    private final JdbcTemplate jdbc;

    public ProviderFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public Provider activeProvider() {
        String suffix = suffix();
        UUID organizationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        String email = "owner-" + suffix + "@example.com";
        jdbc.update("""
                insert into organizations (id, category_id, name, identification_type, identification_number,
                                           contact_email, contact_phone, status, approved_at)
                values (?, (select id from organization_categories order by name limit 1), ?, 'NIT', ?, ?,
                        '6041112233', 'ACTIVE', now())
                """, organizationId, "Proveedor " + suffix, "NIT-" + suffix, email);
        jdbc.update("""
                insert into organization_policies (id, organization_id, version, booking_notice_minutes_min,
                                                   booking_notice_minutes_max, free_cancellation_window_minutes)
                values (?, ?, 1, 0, 43200, 60)
                """, policyId, organizationId);
        jdbc.update("insert into organization_members (organization_id, user_id, role) values (?, ?, 'OWNER')",
                organizationId, ownerId);
        return new Provider(organizationId, ownerId, email, addLocation(organizationId), policyId);
    }

    public UUID addLocation(UUID organizationId) {
        UUID locationId = UUID.randomUUID();
        jdbc.update("""
                insert into locations (id, organization_id, name, address, city_id)
                values (?, ?, ?, 'Calle 1 # 2-3', (select id from cities order by name limit 1))
                """, locationId, organizationId, "Sede " + suffix());
        return locationId;
    }

    /** A person with {@code role} (OWNER, MANAGER or STAFF) in the provider; returns their user id. */
    public UUID addMember(UUID organizationId, String role) {
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into organization_members (organization_id, user_id, role) values (?, ?, ?)",
                organizationId, userId, role);
        return userId;
    }
}
