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

    /** An ACTIVE individual service of 30 minutes, offered in the provider's first location. */
    public UUID activeService(Provider provider) {
        UUID serviceId = UUID.randomUUID();
        jdbc.update("""
                insert into services (id, organization_id, name, duration_minutes, price, currency_id, status)
                values (?, ?, ?, 30, 1000, (select id from currencies where code = 'COP'), 'ACTIVE')
                """, serviceId, provider.organizationId(), "Servicio " + suffix());
        jdbc.update("insert into service_locations (service_id, location_id, organization_id) values (?, ?, ?)",
                serviceId, provider.locationId(), provider.organizationId());
        return serviceId;
    }

    /**
     * A CONFIRMED booking of the service in two days, for a new client, using {@code resourceId}
     * when it is not null. There is no booking module yet, so it is written in plain SQL. The
     * service must be ACTIVE and offered in the provider's first location. Tests must remove it
     * with {@link #deleteBooking}: it points at a client, and the base class deletes every client.
     */
    public UUID futureBooking(Provider provider, UUID serviceId, UUID resourceId) {
        String suffix = suffix();
        UUID client = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        jdbc.update("""
                insert into clients values (?, 'Cliente', ?, '1990-01-01', ?, '3001112233', 'Medellín', 'EMAIL',
                                            'ACTIVE', localtimestamp, localtimestamp)
                """, client, "DOC-" + suffix, "cliente-" + suffix + "@example.com");
        jdbc.update("""
                insert into bookings (id, client_id, organization_id, service_id, location_id, policy_id, session_id,
                                      starts_at, ends_at, total_price, status)
                values (?, ?, ?, ?, ?, ?, ?, date_trunc('minute', now()) + interval '2 days',
                        date_trunc('minute', now()) + interval '2 days 30 minutes', 1000, 'CONFIRMED')
                """, booking, client, provider.organizationId(), serviceId, provider.locationId(),
                provider.policyId(), booking);
        if (resourceId != null) {
            jdbc.update("""
                    insert into booking_resources (booking_id, resource_id, location_id, session_id, starts_at, ends_at,
                                                   status)
                    select id, ?, location_id, session_id, starts_at, ends_at, status from bookings where id = ?
                    """, resourceId, booking);
        }
        return booking;
    }

    public void deleteBooking(UUID bookingId) {
        jdbc.update("delete from booking_resources where booking_id = ?", bookingId);
        jdbc.update("delete from bookings where id = ?", bookingId);
    }
}
