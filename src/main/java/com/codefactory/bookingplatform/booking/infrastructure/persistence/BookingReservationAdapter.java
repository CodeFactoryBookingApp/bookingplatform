package com.codefactory.bookingplatform.booking.infrastructure.persistence;

import com.codefactory.bookingplatform.booking.domain.model.BookingOffering;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingReservationAdapter implements BookingReservations {
    private final JdbcClient jdbc;
    private final String availableSlots;

    public BookingReservationAdapter(JdbcClient jdbc) throws IOException {
        this.jdbc = jdbc;
        availableSlots = new ClassPathResource("booking/available-slots.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<BookingOffering> offering(UUID service, UUID location) {
        return jdbc.sql("""
                select s.name, l.name, o.id, o.name, coalesce(l.timezone,o.timezone), s.price, c.code,
                       s.duration_minutes, s.preparation_minutes, s.cleanup_minutes, s.customer_capacity,
                       p.id, p.version, p.free_cancellation_window_minutes, p.booking_notice_minutes_min,
                       p.booking_notice_minutes_max, p.manual_confirmation
                  from service_locations sl join services s on s.id = sl.service_id
                  join locations l on l.id = sl.location_id join organizations o on o.id = sl.organization_id
                  join organization_policies p on p.organization_id = o.id and p.replaced_at is null
                  join currencies c on c.id = s.currency_id
                 where s.id = ? and l.id = ? and s.status = 'ACTIVE' and o.status = 'ACTIVE' and l.status = 'ACTIVE'
                """).params(service, location).query((r, i) -> new BookingOffering(service, r.getString(1),
                location, r.getString(2), r.getObject(3, UUID.class), r.getString(4), ZoneId.of(r.getString(5)),
                r.getBigDecimal(6), r.getString(7), r.getInt(8), r.getInt(9), r.getInt(10), r.getInt(11),
                r.getObject(12, UUID.class), r.getInt(13), r.getInt(14), r.getInt(15), r.getInt(16),
                r.getBoolean(17))).optional();
    }

    @Override
    public List<Slot> slots(BookingOffering offer, LocalDate day, Instant now) {
        return slotsForReschedule(offer, day, now, null, null);
    }

    @Override
    public List<Slot> slotsForReschedule(BookingOffering offer, LocalDate day, Instant now, UUID ignoredBooking,
                                         UUID preferredResource) {
        return jdbc.sql(availableSlots).param("day", day).param("zone", offer.zone().getId())
                .param("service", offer.serviceId()).param("location", offer.locationId())
                .param("duration", offer.duration()).param("prep", offer.preparation()).param("cleanup", offer.cleanup())
                .param("capacity", offer.capacity())
                .param("earliest", now.plus(offer.minNotice(), ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC))
                .param("latest", now.plus(offer.maxNotice(), ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC))
                .param("ignoreBooking", ignoredBooking)
                .param("preferredResource", preferredResource)
                .query((r, i) -> new Slot(r.getTimestamp("start").toInstant(), r.getTimestamp("finish").toInstant(),
                        r.getObject("session_id", UUID.class), Arrays.asList((UUID[]) r.getArray("ids").getArray()))).list();
    }

    @Override
    public void lockAllocation(UUID service, UUID location, UUID client) {
        // Serialize candidate selection across services competing for the same location's resources.
        jdbc.sql("select id from locations where id = ? for update").param(location).query(UUID.class).optional();
        jdbc.sql("select id from services where id = ? for share").param(service).query(UUID.class).optional();
        jdbc.sql("select id from clients where id = ? for share").param(client).query(UUID.class).optional();
    }

    @Override
    public int recentNoShows(UUID organization, UUID client, Instant since, Instant now) {
        return jdbc.sql("""
                select count(*) from bookings where organization_id = ? and client_id = ? and status = 'NO_SHOW'
                   and starts_at between ? and ?
                """).params(organization, client, since.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC))
                .query(Integer.class).single();
    }

    @Override
    public Receipt insert(BookingOffering offer, Slot slot, UUID client, BookingStatus status) {
        UUID id = UUID.randomUUID();
        UUID session = slot.sessionId() == null ? id : slot.sessionId();
        try {
            var receipt = jdbc.sql("""
                    insert into bookings (id, client_id, organization_id, service_id, location_id, policy_id,
                                          session_id, starts_at, ends_at, total_price, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id, code, status
                    """).params(id, client, offer.organizationId(), offer.serviceId(), offer.locationId(),
                            offer.policyId(), session, slot.start().atOffset(ZoneOffset.UTC),
                            slot.end().atOffset(ZoneOffset.UTC), offer.price(), status.name())
                    .query(BookingReservationAdapter::receipt).single();
            for (UUID resource : slot.resources()) {
                jdbc.sql("""
                        insert into booking_resources (booking_id,resource_id,location_id,session_id,starts_at,ends_at,status)
                        select id, ?, location_id, session_id, starts_at, ends_at, status from bookings where id = ?
                        """).params(resource, id).update();
            }
            history(id, null, status, client);
            return receipt;
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }
    }

    @Override
    public Optional<UUID> lockBookingClient(UUID organization, UUID booking) {
        return jdbc.sql("select client_id from bookings where organization_id = ? and id = ? for update")
                .params(organization, booking).query(UUID.class).optional();
    }

    @Override
    public Receipt confirm(UUID booking, UUID actor) {
        var receipt = jdbc.sql("""
                update bookings set status = 'CONFIRMED' where id = ? and status = 'RESERVED' returning id,code,status
                """).param(booking).query(BookingReservationAdapter::receipt).optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
        history(booking, BookingStatus.RESERVED, BookingStatus.CONFIRMED, actor, null);
        return receipt;
    }

    @Override
    public Optional<CancellationTarget> lockForClientCancellation(UUID client, UUID booking) {
        return jdbc.sql("""
                select b.id, b.client_id, b.organization_id, b.status, b.starts_at, p.free_cancellation_window_minutes
                  from bookings b join organization_policies p on p.id = b.policy_id
                 where b.client_id = ? and b.id = ? for update of b
                """).params(client, booking).query(BookingReservationAdapter::cancellationTarget).optional();
    }

    @Override
    public Optional<CancellationTarget> lockForProviderCancellation(UUID organization, UUID booking) {
        return jdbc.sql("""
                select b.id, b.client_id, b.organization_id, b.status, b.starts_at, p.free_cancellation_window_minutes
                  from bookings b join organization_policies p on p.id = b.policy_id
                 where b.organization_id = ? and b.id = ? for update of b
                """).params(organization, booking).query(BookingReservationAdapter::cancellationTarget).optional();
    }

    @Override
    public CancellationReceipt cancel(CancellationTarget target, Instant cancelledAt, boolean lateCancellation,
                                      String reason, UUID actor) {
        var receipt = jdbc.sql("""
                update bookings set status = 'CANCELLED', cancelled_at = ?, late_cancellation = ?
                 where id = ? and status in ('RESERVED', 'CONFIRMED')
                 returning id, code, status, late_cancellation
                """).params(cancelledAt.atOffset(ZoneOffset.UTC), lateCancellation, target.id())
                .query((r, i) -> new CancellationReceipt(r.getObject("id", UUID.class), r.getString("code"),
                        BookingStatus.valueOf(r.getString("status")), r.getBoolean("late_cancellation"))).optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
        jdbc.sql("delete from booking_resources where booking_id = ?").param(target.id()).update();
        history(target.id(), target.status(), BookingStatus.CANCELLED, actor, reason);
        return receipt;
    }

    @Override
    public Optional<RescheduleTarget> lockForReschedule(UUID client, UUID booking) {
        return jdbc.sql("""
                select b.id, b.code, b.client_id, b.organization_id, b.service_id, b.location_id, b.session_id,
                       b.status, b.starts_at, b.ends_at, p.free_cancellation_window_minutes, p.max_reschedules,
                       b.reschedule_count, b.late_reschedule,
                       coalesce((select array_agg(br.resource_id order by br.resource_id)
                                   from booking_resources br where br.booking_id = b.id), array[]::uuid[]) as resource_ids
                  from bookings b join organization_policies p on p.id = b.policy_id
                 where b.client_id = ? and b.id = ? for update of b
                """).params(client, booking).query(BookingReservationAdapter::rescheduleTarget).optional();
    }

    @Override
    public RescheduleReceipt reschedule(RescheduleTarget target, UUID targetLocation, Slot slot, boolean lateReschedule) {
        UUID session = slot.sessionId() == null ? target.id() : slot.sessionId();
        boolean markLate = target.lateReschedule() || lateReschedule;
        try {
            jdbc.sql("delete from booking_resources where booking_id = ?").param(target.id()).update();
            var receipt = jdbc.sql("""
                    update bookings
                       set location_id = ?, session_id = ?, starts_at = ?, ends_at = ?,
                           reschedule_count = reschedule_count + 1, late_reschedule = ?
                     where id = ? and status in ('RESERVED', 'CONFIRMED')
                     returning id, code, status, location_id, starts_at, ends_at, reschedule_count, late_reschedule
                    """).params(targetLocation, session, slot.start().atOffset(ZoneOffset.UTC),
                            slot.end().atOffset(ZoneOffset.UTC), markLate, target.id())
                    .query((r, i) -> new RescheduleReceipt(r.getObject("id", UUID.class), r.getString("code"),
                            BookingStatus.valueOf(r.getString("status")), r.getObject("location_id", UUID.class),
                            r.getTimestamp("starts_at").toInstant(), r.getTimestamp("ends_at").toInstant(),
                            r.getInt("reschedule_count"), r.getBoolean("late_reschedule"))).optional()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
            for (UUID resource : slot.resources()) {
                jdbc.sql("""
                        insert into booking_resources (booking_id,resource_id,location_id,session_id,starts_at,ends_at,status)
                        select id, ?, location_id, session_id, starts_at, ends_at, status from bookings where id = ?
                        """).params(resource, target.id()).update();
            }
            history(target.id(), target.status(), target.status(), target.clientId(),
                    lateReschedule ? "Reprogramación tardía del cliente" : "Reprogramación del cliente");
            return receipt;
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrityViolation(ex);
        }
    }

    @Override
    public Optional<AttendanceTarget> lockForAttendance(UUID organization, UUID booking) {
        return jdbc.sql("""
                select b.id, b.code, b.client_id, b.organization_id, b.status, b.starts_at, b.ends_at,
                       (select c.changed_at from booking_status_changes c
                         where c.booking_id = b.id and c.to_status in ('COMPLETED', 'NO_SHOW')
                         order by c.changed_at desc limit 1) as closed_at
                  from bookings b
                 where b.organization_id = ? and b.id = ? for update of b
                """).params(organization, booking).query(BookingReservationAdapter::attendanceTarget).optional();
    }

    @Override
    public AttendanceReceipt closeAttendance(AttendanceTarget target, BookingStatus status, UUID actor,
                                             Instant closedAt, String observation) {
        jdbc.sql("""
                update bookings set status = ?
                 where id = ? and status in ('RESERVED', 'CONFIRMED', 'COMPLETED', 'NO_SHOW')
                """).params(status.name(), target.id()).update();
        jdbc.sql("""
                insert into booking_status_changes (booking_id, from_status, to_status, reason, changed_by, changed_at)
                values (?, ?, ?, ?, ?, ?)
                """).params(target.id(), target.status().name(), status.name(), observation, actor,
                closedAt.atOffset(ZoneOffset.UTC)).update();
        int noShows = jdbc.sql("""
                select count(*) from bookings where organization_id = ? and client_id = ? and status = 'NO_SHOW'
                """).params(target.organizationId(), target.clientId()).query(Integer.class).single();
        return new AttendanceReceipt(target.id(), target.code(), status, actor, closedAt, observation, noShows);
    }

    @Override
    public int autoCloseOverdue(Instant now) {
        return jdbc.sql("""
                with due as (
                    select b.id, b.status as from_status, p.default_closure_status as to_status
                      from bookings b join organization_policies p on p.id = b.policy_id
                     where b.status in ('RESERVED', 'CONFIRMED')
                       and b.ends_at + (p.auto_close_delay_hours * interval '1 hour') <= ?
                     for update of b skip locked
                ), closed as (
                    update bookings b
                       set status = due.to_status
                      from due
                     where b.id = due.id
                    returning b.id, due.from_status, due.to_status
                )
                insert into booking_status_changes (booking_id, from_status, to_status, reason, changed_by, changed_at)
                select id, from_status, to_status, 'Cierre automático por vencimiento de plazo', null, ?
                  from closed
                """).params(now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC)).update();
    }

    private static RuntimeException translateIntegrityViolation(DataIntegrityViolationException ex) {
        if (ex.getMostSpecificCause() instanceof SQLException sql) {
            return switch (sql.getSQLState()) {
                case "23P12" -> new BusinessException(ErrorCode.CLIENT_BOOKING_OVERLAP);
                case "23P01" -> new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
                default -> ex;
            };
        }
        return ex;
    }

    private void history(UUID booking, BookingStatus from, BookingStatus to, UUID actor) {
        history(booking, from, to, actor, null);
    }

    private void history(UUID booking, BookingStatus from, BookingStatus to, UUID actor, String reason) {
        jdbc.sql("""
                insert into booking_status_changes (booking_id,from_status,to_status,reason,changed_by)
                values (?,?,?,?,?)
                """).param(booking).param(from == null ? null : from.name()).param(to.name()).param(reason).param(actor)
                .update();
    }

    private static CancellationTarget cancellationTarget(ResultSet row, int index) throws SQLException {
        return new CancellationTarget(row.getObject("id", UUID.class), row.getObject("client_id", UUID.class),
                row.getObject("organization_id", UUID.class), BookingStatus.valueOf(row.getString("status")),
                row.getTimestamp("starts_at").toInstant(), row.getInt("free_cancellation_window_minutes"));
    }

    private static RescheduleTarget rescheduleTarget(ResultSet row, int index) throws SQLException {
        return new RescheduleTarget(row.getObject("id", UUID.class), row.getString("code"),
                row.getObject("client_id", UUID.class), row.getObject("organization_id", UUID.class),
                row.getObject("service_id", UUID.class), row.getObject("location_id", UUID.class),
                row.getObject("session_id", UUID.class), BookingStatus.valueOf(row.getString("status")),
                row.getTimestamp("starts_at").toInstant(), row.getTimestamp("ends_at").toInstant(),
                row.getInt("free_cancellation_window_minutes"), row.getInt("max_reschedules"),
                row.getInt("reschedule_count"), row.getBoolean("late_reschedule"),
                Arrays.asList((UUID[]) row.getArray("resource_ids").getArray()));
    }

    private static AttendanceTarget attendanceTarget(ResultSet row, int index) throws SQLException {
        var closedAt = row.getTimestamp("closed_at");
        return new AttendanceTarget(row.getObject("id", UUID.class), row.getString("code"),
                row.getObject("client_id", UUID.class), row.getObject("organization_id", UUID.class),
                BookingStatus.valueOf(row.getString("status")), row.getTimestamp("starts_at").toInstant(),
                row.getTimestamp("ends_at").toInstant(), closedAt == null ? null : closedAt.toInstant());
    }

    private static Receipt receipt(ResultSet row, int index) throws SQLException {
        return new Receipt(row.getObject("id", UUID.class), row.getString("code"), BookingStatus.valueOf(row.getString("status")));
    }
}
