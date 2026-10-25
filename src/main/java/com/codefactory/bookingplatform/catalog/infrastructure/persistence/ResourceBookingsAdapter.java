package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.port.ResourceBookings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Reads booking_resources in plain SQL: the booking module does not exist yet, so there is no
 * facade to ask. When it does, this adapter calls it instead and the use cases stay the same.
 */
@Repository
public class ResourceBookingsAdapter implements ResourceBookings {

    private static final String COUNT_FUTURE = """
            select count(distinct booking_id)
              from booking_resources
             where resource_id = ?
               and status in ('RESERVED', 'CONFIRMED')
               and ends_at > ?
            """;

    private final JdbcTemplate jdbc;

    public ResourceBookingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countFuture(UUID resourceId, Instant now) {
        Long count = jdbc.queryForObject(COUNT_FUTURE, Long.class, resourceId, Timestamp.from(now));
        return count == null ? 0 : count;
    }
}
