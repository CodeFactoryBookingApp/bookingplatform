package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.ClientSummary;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.domain.port.ClientListing;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-003 client listing in SQL. Every filter is a bound parameter; the only text in the SQL is the
 * sort column, taken from a fixed list.
 *
 * <p>clients.created_at is a timestamp without zone holding UTC, as Hibernate writes it
 * ({@code hibernate.jdbc.time_zone: UTC}); it is read as UTC and the registration day is taken in
 * the platform's zone.
 */
@Repository
public class ClientListingAdapter implements ClientListing {

    private static final String REGISTRATION_DAY =
            "((c.created_at at time zone 'UTC') at time zone 'America/Bogota')::date";

    private static final String COLUMNS = """
            select c.id, c.full_name, c.document, c.email, c.phone, c.city, c.notification_channel, c.status,
                   c.created_at
            """;

    private static final String FROM = """
              from clients c
             where true
            """;

    private static final Map<ClientFilter.Sort, String> SORT_COLUMNS = new EnumMap<>(Map.of(
            ClientFilter.Sort.NAME, "c.full_name",
            ClientFilter.Sort.EMAIL, "c.email",
            ClientFilter.Sort.CITY, "c.city",
            ClientFilter.Sort.STATUS, "c.status",
            ClientFilter.Sort.REGISTERED, "c.created_at"));

    private static final RowMapper<ClientSummary> ROW = (row, n) -> new ClientSummary(
            row.getObject("id", UUID.class), row.getString("full_name"), row.getString("document"),
            row.getString("email"), row.getString("phone"), row.getString("city"),
            NotificationChannel.valueOf(row.getString("notification_channel")),
            ClientStatus.valueOf(row.getString("status")),
            row.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC));

    private final JdbcTemplate jdbc;

    public ClientListingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every value is a bound parameter; the only text put into the SQL is the sort column, taken
    // from a fixed map, never from the request.
    @SuppressWarnings("java:S2077")
    @Override
    public ResultPage<ClientSummary> find(ClientFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long total = jdbc.queryForObject("select count(*) " + where, Long.class, args.toArray());
        args.add(filter.size());
        args.add((long) filter.page() * filter.size());
        List<ClientSummary> rows = jdbc.query(COLUMNS + where + order(filter) + " limit ? offset ?", ROW,
                args.toArray());
        return new ResultPage<>(rows, total == null ? 0 : total, filter.page(), filter.size());
    }

    // Every value is a bound parameter; the only text put into the SQL is the sort column, taken
    // from a fixed map, never from the request.
    @SuppressWarnings("java:S2077")
    @Override
    public List<ClientSummary> findAll(ClientFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        args.add(MAX_EXPORT);
        return jdbc.query(COLUMNS + where + order(filter) + " limit ?", ROW, args.toArray());
    }

    private static String where(ClientFilter filter, List<Object> args) {
        StringBuilder where = new StringBuilder(FROM);
        if (filter.status() != null) {
            where.append("   and c.status = ?\n");
            args.add(filter.status().name());
        }
        if (filter.city() != null) {
            where.append("   and f_sin_tildes(c.city) = f_sin_tildes(?)\n");
            args.add(filter.city());
        }
        if (filter.registeredFrom() != null) {
            where.append("   and ").append(REGISTRATION_DAY).append(" >= ?\n");
            args.add(filter.registeredFrom());
        }
        if (filter.registeredTo() != null) {
            where.append("   and ").append(REGISTRATION_DAY).append(" <= ?\n");
            args.add(filter.registeredTo());
        }
        return where.toString();
    }

    private static String order(ClientFilter filter) {
        return " order by " + SORT_COLUMNS.get(filter.sort()) + (filter.descending() ? " desc" : " asc")
                + ", c.full_name, c.id";
    }
}
