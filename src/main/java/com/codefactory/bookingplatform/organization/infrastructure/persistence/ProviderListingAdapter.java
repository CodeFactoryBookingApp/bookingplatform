package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.model.ProviderFilter;
import com.codefactory.bookingplatform.organization.domain.model.ProviderSummary;
import com.codefactory.bookingplatform.organization.domain.port.ProviderListing;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-003 provider listing in SQL. Every filter is a bound parameter; the only text in the SQL is
 * the sort column, taken from a fixed list.
 */
@Repository
public class ProviderListingAdapter implements ProviderListing {

    /** Registration dates are days in the platform's zone. */
    static final String PLATFORM_ZONE = "America/Bogota";

    private static final String COLUMNS = """
            select o.id, o.name, o.identification_type, o.identification_number, oc.name as category,
                   o.contact_email, o.contact_phone, o.status, o.created_at, o.approved_at,
                   (select string_agg(distinct c.name, ', ' order by c.name)
                      from locations l join cities c on c.id = l.city_id
                     where l.organization_id = o.id) as cities
            """;

    private static final String FROM = """
              from organizations o
              join organization_categories oc on oc.id = o.category_id
             where true
            """;

    private static final Map<ProviderFilter.Sort, String> SORT_COLUMNS = new EnumMap<>(Map.of(
            ProviderFilter.Sort.NAME, "o.name",
            ProviderFilter.Sort.STATUS, "o.status",
            ProviderFilter.Sort.CATEGORY, "oc.name",
            ProviderFilter.Sort.REGISTERED, "o.created_at"));

    private static final RowMapper<ProviderSummary> ROW = (row, n) -> new ProviderSummary(
            row.getObject("id", UUID.class), row.getString("name"),
            IdentificationType.valueOf(row.getString("identification_type")), row.getString("identification_number"),
            row.getString("category"), row.getString("contact_email"), row.getString("contact_phone"),
            row.getString("cities"), OrganizationStatus.valueOf(row.getString("status")),
            instant(row.getTimestamp("created_at")), instant(row.getTimestamp("approved_at")));

    private final JdbcTemplate jdbc;

    public ProviderListingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every value is a bound parameter; the only text put into the SQL is the sort column, taken
    // from a fixed map, never from the request.
    @SuppressWarnings("java:S2077")
    @Override
    public ResultPage<ProviderSummary> find(ProviderFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long total = jdbc.queryForObject("select count(*) " + where, Long.class, args.toArray());
        args.add(filter.size());
        args.add((long) filter.page() * filter.size());
        List<ProviderSummary> rows = jdbc.query(COLUMNS + where + order(filter) + " limit ? offset ?", ROW,
                args.toArray());
        return new ResultPage<>(rows, total == null ? 0 : total, filter.page(), filter.size());
    }

    // Every value is a bound parameter; the only text put into the SQL is the sort column, taken
    // from a fixed map, never from the request.
    @SuppressWarnings("java:S2077")
    @Override
    public List<ProviderSummary> findAll(ProviderFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        args.add(MAX_EXPORT);
        return jdbc.query(COLUMNS + where + order(filter) + " limit ?", ROW, args.toArray());
    }

    private static String where(ProviderFilter filter, List<Object> args) {
        StringBuilder where = new StringBuilder(FROM);
        if (filter.status() != null) {
            where.append("   and o.status = ?\n");
            args.add(filter.status().name());
        }
        if (filter.categoryId() != null) {
            where.append("   and o.category_id = ?\n");
            args.add(filter.categoryId());
        }
        if (filter.city() != null) {
            where.append("""
                       and exists (select 1 from locations l join cities c on c.id = l.city_id
                                    where l.organization_id = o.id and f_sin_tildes(c.name) = f_sin_tildes(?))
                    """);
            args.add(filter.city());
        }
        if (filter.registeredFrom() != null) {
            where.append("   and (o.created_at at time zone '").append(PLATFORM_ZONE).append("')::date >= ?\n");
            args.add(filter.registeredFrom());
        }
        if (filter.registeredTo() != null) {
            where.append("   and (o.created_at at time zone '").append(PLATFORM_ZONE).append("')::date <= ?\n");
            args.add(filter.registeredTo());
        }
        return where.toString();
    }

    private static String order(ProviderFilter filter) {
        return " order by " + SORT_COLUMNS.get(filter.sort()) + (filter.descending() ? " desc" : " asc")
                + ", o.name, o.id";
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
