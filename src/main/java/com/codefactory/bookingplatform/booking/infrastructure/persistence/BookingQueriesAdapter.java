package com.codefactory.bookingplatform.booking.infrastructure.persistence;

import com.codefactory.bookingplatform.booking.domain.model.BookingRates;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Filter;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Row;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Summary;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingQueries;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Repository
public class BookingQueriesAdapter implements BookingQueries {

    private static final String FILTERS = """
             where b.organization_id = :organization
               and (cast(:fromTime as timestamptz) is null or b.starts_at >= :fromTime)
               and (cast(:toTime as timestamptz) is null or b.starts_at < :toTime)
               and (cast(:status as varchar) is null or b.status = :status)
               and (cast(:service as uuid) is null or b.service_id = :service)
               and (cast(:location as uuid) is null or b.location_id = :location)
               and (cast(:client as uuid) is null or b.client_id = :client)
               and (cast(:code as varchar) is null or b.code = :code)
               and (cast(:resource as uuid) is null or exists (
                   select 1 from booking_resources br where br.booking_id = b.id and br.resource_id = :resource))
            """;
    private static final String ROWS = """
            select b.id, b.code, b.client_id, c.full_name, b.service_id, s.name as service,
                   b.location_id, l.name as location, b.starts_at, b.ends_at, b.attendees, b.status,
                   b.total_price, currency.code as currency
              from bookings b
              join clients c on c.id = b.client_id
              join services s on s.id = b.service_id
              join locations l on l.id = b.location_id
              join currencies currency on currency.id = b.currency_id
            """ + FILTERS + " order by b.starts_at desc, b.id";

    private final JdbcClient jdbc;

    public BookingQueriesAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> search(UUID organizationId, Filter filter, int limit, long offset) {
        return filtered(ROWS + " limit :limit offset :offset", organizationId, filter)
                .param("limit", limit).param("offset", offset).query(BookingQueriesAdapter::map).list();
    }

    @Override
    public List<Row> export(UUID organizationId, Filter filter) {
        return filtered(ROWS, organizationId, filter).query(BookingQueriesAdapter::map).list();
    }

    @Override
    public Summary summarize(UUID organizationId, Filter filter) {
        var groups = filtered("""
                select b.status, c.code as currency, count(*) as total, sum(b.total_price) as amount
                  from bookings b join currencies c on c.id = b.currency_id
                """ + FILTERS + " group by b.status, c.code", organizationId, filter)
                .query((rs, n) -> new Group(BookingStatus.valueOf(rs.getString("status")), rs.getString("currency"),
                        rs.getLong("total"), rs.getBigDecimal("amount"))).list();
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status, 0L);
        }
        Map<String, BigDecimal> amounts = new TreeMap<>();
        long total = 0;
        for (Group group : groups) {
            counts.merge(group.status(), group.total(), Long::sum);
            amounts.merge(group.currency(), group.amount(), BigDecimal::add);
            total += group.total();
        }
        return new Summary(total, counts, amounts, BookingRates.percentage(counts.get(BookingStatus.CANCELLED), total));
    }

    private JdbcClient.StatementSpec filtered(String sql, UUID organizationId, Filter filter) {
        return jdbc.sql(sql).param("organization", organizationId)
                .param("fromTime", utc(filter.from())).param("toTime", utc(filter.to()))
                .param("status", filter.status() == null ? null : filter.status().name())
                .param("service", filter.serviceId()).param("location", filter.locationId())
                .param("resource", filter.resourceId()).param("client", filter.clientId()).param("code", filter.code());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Row map(ResultSet rs, int row) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("code"), rs.getObject("client_id", UUID.class),
                rs.getString("full_name"), rs.getObject("service_id", UUID.class), rs.getString("service"),
                rs.getObject("location_id", UUID.class), rs.getString("location"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getInt("attendees"),
                rs.getBigDecimal("total_price"), rs.getString("currency"), BookingStatus.valueOf(rs.getString("status")));
    }

    private record Group(BookingStatus status, String currency, long total, BigDecimal amount) {
    }
}
