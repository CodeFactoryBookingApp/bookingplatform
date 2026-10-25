package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.port.OrganizationBookings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Reads bookings in plain SQL until there is a booking module to ask. */
@Repository
public class OrganizationBookingsAdapter implements OrganizationBookings {

    private final JdbcTemplate jdbc;

    public OrganizationBookingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countFuture(UUID organizationId, Instant now) {
        Integer count = jdbc.queryForObject("""
                select count(*) from bookings
                 where organization_id = ? and status in ('RESERVED', 'CONFIRMED') and starts_at > ?
                """, Integer.class, organizationId, Timestamp.from(now));
        return count == null ? 0 : count;
    }
}
