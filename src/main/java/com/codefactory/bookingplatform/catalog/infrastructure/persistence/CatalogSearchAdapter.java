package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.CatalogCriteria;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogEntry;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogPage;
import com.codefactory.bookingplatform.catalog.domain.port.CatalogSearch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-005 search in SQL. Only active services, of active providers, in active locations, are
 * cards: a pending or suspended provider never shows (HU-002, HU-003). Every filter is a bound
 * parameter; the only text that goes into the SQL is the sort column, taken from a fixed list.
 */
@Repository
public class CatalogSearchAdapter implements CatalogSearch {

    private static final String FROM = """
              from services s
              join organizations o            on o.id = s.organization_id and o.status = 'ACTIVE'
              join organization_categories oc on oc.id = o.category_id
              join service_locations sl       on sl.service_id = s.id
              join locations l                on l.id = sl.location_id and l.status = 'ACTIVE'
              join cities c                   on c.id = l.city_id
             where s.status = 'ACTIVE'
            """;

    private static final String COLUMNS = """
            select s.id as service_id, s.name as service_name, s.description, s.duration_minutes, s.price,
                   s.customer_capacity, o.id as provider_id, o.name as provider_name, oc.name as category,
                   l.id as location_id, l.name as location_name, c.name as city, c.region
            """;

    private static final Map<CatalogCriteria.Sort, String> SORT_COLUMNS = new EnumMap<>(Map.of(
            CatalogCriteria.Sort.NAME, "s.name",
            CatalogCriteria.Sort.PRICE, "s.price",
            CatalogCriteria.Sort.DURATION, "s.duration_minutes",
            CatalogCriteria.Sort.PROVIDER, "o.name"));

    private static final RowMapper<CatalogEntry> ENTRY = (row, n) -> new CatalogEntry(
            row.getObject("service_id", UUID.class), row.getString("service_name"), row.getString("description"),
            row.getInt("duration_minutes"), row.getBigDecimal("price"), row.getInt("customer_capacity"),
            row.getObject("provider_id", UUID.class), row.getString("provider_name"), row.getString("category"),
            row.getObject("location_id", UUID.class), row.getString("location_name"), row.getString("city"),
            row.getString("region"));

    private final JdbcTemplate jdbc;

    public CatalogSearchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every value is a bound parameter; the only text put into the SQL is the sort column, taken
    // from a fixed map, never from the request.
    @SuppressWarnings("java:S2077")
    @Override
    public CatalogPage search(CatalogCriteria criteria) {
        StringBuilder where = new StringBuilder(FROM);
        List<Object> args = new ArrayList<>();
        if (criteria.keyword() != null) {
            where.append("""
                       and (f_sin_tildes(s.name) like f_sin_tildes(?) escape '\\'
                            or f_sin_tildes(coalesce(s.description, '')) like f_sin_tildes(?) escape '\\'
                            or f_sin_tildes(o.name) like f_sin_tildes(?) escape '\\')
                    """);
            String pattern = "%" + likeLiteral(criteria.keyword()) + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        filter(where, args, "o.category_id = ?", criteria.categoryId());
        filter(where, args, "l.city_id = ?", criteria.cityId());
        filter(where, args, "o.id = ?", criteria.providerId());
        filter(where, args, "s.price >= ?", criteria.minPrice());
        filter(where, args, "s.price <= ?", criteria.maxPrice());

        Long total = jdbc.queryForObject("select count(*) " + where, Long.class, args.toArray());

        String order = " order by " + SORT_COLUMNS.get(criteria.sort()) + (criteria.descending() ? " desc" : " asc")
                + ", s.name, l.name, s.id, l.id limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(criteria.size());
        pageArgs.add((long) criteria.page() * criteria.size());
        List<CatalogEntry> entries = jdbc.query(COLUMNS + where + order, ENTRY, pageArgs.toArray());
        return new CatalogPage(entries, total == null ? 0 : total, criteria.page(), criteria.size());
    }

    private static void filter(StringBuilder where, List<Object> args, String condition, Object value) {
        if (value != null) {
            where.append("   and ").append(condition).append('\n');
            args.add(value);
        }
    }

    /**
     * The keyword is folded like the columns (lower case, no accents) by f_sin_tildes in SQL; here
     * only LIKE's own wildcards are escaped, so that "%" or "_" typed by a client match literally.
     */
    static String likeLiteral(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
