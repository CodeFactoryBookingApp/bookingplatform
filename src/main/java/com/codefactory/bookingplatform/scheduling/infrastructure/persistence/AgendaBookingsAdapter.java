package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads bookings in plain SQL until there is a booking module to ask. */
@Repository
public class AgendaBookingsAdapter implements AgendaBookings {

    private static final String LOCATION_AGENDA = """
            select b.id, b.starts_at, b.ends_at
              from bookings b
             where b.location_id = ?
               and b.status in ('RESERVED', 'CONFIRMED')
               and b.ends_at > ?
               and b.service_id in (select service_id from agenda_services where agenda_id = ?)
             order by b.starts_at
            """;

    private static final String RESOURCE_AGENDA = """
            select b.id, b.starts_at, b.ends_at
              from bookings b
             where b.location_id = ?
               and b.status in ('RESERVED', 'CONFIRMED')
               and b.ends_at > ?
               and b.service_id in (select service_id from agenda_services where agenda_id = ?)
               and exists (select 1 from booking_resources br where br.booking_id = b.id and br.resource_id = ?)
             order by b.starts_at
            """;

    private static final RowMapper<BookedSlot> SLOT = (row, n) -> new BookedSlot(
            row.getObject("id", UUID.class),
            row.getTimestamp("starts_at").toInstant(),
            row.getTimestamp("ends_at").toInstant());

    private final JdbcTemplate jdbc;

    public AgendaBookingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BookedSlot> future(Agenda agenda, Instant now) {
        Timestamp after = Timestamp.from(now);
        return agenda.getResourceId() == null
                ? jdbc.query(LOCATION_AGENDA, SLOT, agenda.getLocationId(), after, agenda.getId())
                : jdbc.query(RESOURCE_AGENDA, SLOT, agenda.getLocationId(), after, agenda.getId(),
                agenda.getResourceId());
    }
}
