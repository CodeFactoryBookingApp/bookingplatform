package com.codefactory.bookingplatform.support;

import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BookingFixture {

    private final JdbcTemplate jdbc;
    private final List<UUID> ids = new ArrayList<>();

    public BookingFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Booking create(Provider provider, UUID serviceId, Appointment slot) {
        UUID id = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        String email = "client-" + client + "@example.com";
        jdbc.update("""
                insert into clients values (?, 'Cliente de prueba', ?, '1990-01-01', ?, '3001112233', 'Cali', 'EMAIL',
                                            'ACTIVE', localtimestamp, localtimestamp)
                """, client, client.toString().substring(0, 23), email);
        jdbc.update("""
                insert into bookings (id, client_id, organization_id, service_id, location_id, policy_id, session_id,
                                      starts_at, ends_at, created_at, total_price, status, cancelled_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, case when ? = 'CANCELLED' then now() end)
                """, id, client, provider.organizationId(), serviceId, provider.locationId(), provider.policyId(), id,
                slot.startsAt().atOffset(ZoneOffset.UTC), slot.startsAt().plusSeconds(1800).atOffset(ZoneOffset.UTC),
                slot.createdAt().atOffset(ZoneOffset.UTC), slot.amount(), slot.status(), slot.status());
        ids.add(id);
        return new Booking(id, client, email, jdbc.queryForObject("select code from bookings where id = ?", String.class, id));
    }

    public UUID resource(Provider provider) {
        UUID type = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        jdbc.update("insert into resource_types (id, organization_id, name) values (?, ?, ?)",
                type, provider.organizationId(), "Tipo " + type);
        jdbc.update("""
                insert into resources (id, resource_type_id, location_id, name, organization_id)
                values (?, ?, ?, ?, ?)
                """, resource, type, provider.locationId(), "Recurso " + resource, provider.organizationId());
        return resource;
    }

    public void assign(UUID bookingId, UUID resourceId) {
        jdbc.update("""
                insert into booking_resources (booking_id, resource_id, location_id, session_id, starts_at, ends_at, status)
                select id, ?, location_id, session_id, starts_at, ends_at, status from bookings where id = ?
                """, resourceId, bookingId);
    }

    public void clean() {
        ids.forEach(new ProviderFixture(jdbc)::deleteBooking);
    }

    public record Booking(UUID id, UUID clientId, String clientEmail, String code) {
    }

    public record Appointment(Instant startsAt, Instant createdAt, String status, int amount) {
    }
}
