package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.port.ServiceBookings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Reads bookings in plain SQL until there is a booking module to ask (see ResourceBookingsAdapter). */
@Repository
public class ServiceBookingsAdapter implements ServiceBookings {

    private final JdbcTemplate jdbc;

    public ServiceBookingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exist(UUID serviceId) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from bookings where service_id = ?)",
                Boolean.class, serviceId);
        return Boolean.TRUE.equals(exists);
    }
}
