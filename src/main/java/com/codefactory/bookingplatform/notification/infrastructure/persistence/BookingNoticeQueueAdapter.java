package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Delivery;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Kind;
import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingNoticeQueueAdapter implements BookingNotices {

    private final JdbcClient jdbc;

    public BookingNoticeQueueAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Candidate> due(Instant at, int limit) {
        return jdbc.sql("""
                select id, booking_id from booking_notices
                where processed_at is null and due_at <= :at order by due_at, id limit :limit
                """).param("at", utc(at)).param("limit", limit)
                .query((rs, n) -> new Candidate(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class)))
                .list();
    }

    @Override
    public boolean lockBooking(UUID bookingId) {
        return jdbc.sql("select id from bookings where id = :id for share skip locked")
                .param("id", bookingId).query(UUID.class).optional().isPresent();
    }

    @Override
    public Optional<Delivery> claim(UUID id, Instant at) {
        return jdbc.sql("""
                select n.id, n.kind, n.appointment_at, n.appointment_status, b.code, b.status as current_status,
                       s.name as service, l.name as location, coalesce(l.timezone, o.timezone) as appointment_zone,
                       case n.recipient when 'CLIENT' then c.email else o.contact_email end as email,
                       case n.recipient when 'CLIENT' then c.id else nullif(own.user_id, c.id) end as recipient_id,
                       p.user_id as preference_owner, p.reminders_enabled, p.quiet_from, p.quiet_until, p.timezone
                  from booking_notices n
                  join bookings b on b.id = n.booking_id
                  join clients c on c.id = b.client_id
                  join services s on s.id = b.service_id
                  join organizations o on o.id = b.organization_id
                  left join locations l on l.id = n.location_id
                  left join lateral (
                      select user_id from organization_members
                       where organization_id = o.id and role = 'OWNER' order by created_at, id limit 1
                  ) own on n.recipient = 'PROVIDER'
                  left join booking_notification_preferences p on p.user_id =
                      case n.recipient when 'CLIENT' then c.id else own.user_id end
                 where n.id = :id and n.processed_at is null and n.due_at <= :at
                 for update of n skip locked
                """).param("id", id).param("at", utc(at)).query(BookingNoticeQueueAdapter::map).optional();
    }

    @Override
    public void markProcessed(UUID id, Instant at) {
        jdbc.sql("update booking_notices set processed_at = :at where id = :id")
                .param("at", utc(at)).param("id", id).update();
    }

    @Override
    public void defer(UUID id, Instant at) {
        jdbc.sql("update booking_notices set due_at = :at where id = :id")
                .param("at", utc(at)).param("id", id).update();
    }

    private static Delivery map(ResultSet rs, int row) throws SQLException {
        ReminderPreferences preferences = rs.getObject("preference_owner") == null ? ReminderPreferences.DEFAULTS
                : new ReminderPreferences(rs.getBoolean("reminders_enabled"),
                        rs.getObject("quiet_from", LocalTime.class), rs.getObject("quiet_until", LocalTime.class),
                        rs.getString("timezone"));
        return new Delivery(rs.getObject("id", UUID.class), Kind.valueOf(rs.getString("kind")),
                rs.getObject("recipient_id", UUID.class), rs.getString("email"),
                rs.getString("code"), rs.getString("service"), rs.getString("location"),
                rs.getObject("appointment_at", OffsetDateTime.class).toInstant(), rs.getString("appointment_status"),
                rs.getString("appointment_zone"), rs.getString("current_status"), preferences);
    }

    private static OffsetDateTime utc(Instant time) {
        return time.atOffset(ZoneOffset.UTC);
    }
}
