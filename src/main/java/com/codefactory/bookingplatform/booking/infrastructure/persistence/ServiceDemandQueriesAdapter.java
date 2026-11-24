package com.codefactory.bookingplatform.booking.infrastructure.persistence;

import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Bucket;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Group;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Window;
import com.codefactory.bookingplatform.booking.domain.port.ServiceDemandQueries;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class ServiceDemandQueriesAdapter implements ServiceDemandQueries {

    private final JdbcClient jdbc;

    public ServiceDemandQueriesAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String timezone(UUID organizationId) {
        return jdbc.sql("select timezone from organizations where id = :id").param("id", organizationId)
                .query(String.class).single();
    }

    @Override
    public List<Group> metrics(UUID organizationId, Window period) {
        return period("""
                select b.service_id, s.name, b.status, c.code as currency, count(*) as bookings,
                       sum(b.total_price) as amount
                  from bookings b
                  join services s on s.id = b.service_id
                  join currencies c on c.id = b.currency_id
                 where b.organization_id = :organization and b.created_at >= :fromTime and b.created_at < :toTime
                 group by b.service_id, s.name, b.status, c.code
                """, organizationId, period).query((rs, n) -> new Group(rs.getObject("service_id", UUID.class),
                rs.getString("name"), BookingStatus.valueOf(rs.getString("status")), rs.getString("currency"),
                rs.getLong("bookings"), rs.getBigDecimal("amount"))).list();
    }

    @Override
    public List<Bucket> demand(UUID organizationId, Window period, String timezone) {
        var rules = ZoneId.of(timezone).getRules();
        Integer fixedOffset = rules.isFixedOffset() ? rules.getOffset(period.from()).getTotalSeconds() : null;
        return period("""
                select extract(isodow from local_start)::int as day_of_week,
                       extract(hour from local_start)::int as hour_of_day, count(*) as bookings
                  from (
                      select case when cast(:fixedOffset as int) is null then timezone(:zone, b.starts_at)
                                  else timezone(make_interval(secs => :fixedOffset), b.starts_at) end as local_start
                        from bookings b
                       where b.organization_id = :organization and b.starts_at >= :fromTime and b.starts_at < :toTime
                  ) appointments
                 group by day_of_week, hour_of_day order by day_of_week, hour_of_day
                """, organizationId, period).param("zone", timezone).param("fixedOffset", fixedOffset)
                .query((rs, n) -> new Bucket(rs.getInt("day_of_week"), rs.getInt("hour_of_day"),
                        rs.getLong("bookings"))).list();
    }

    private JdbcClient.StatementSpec period(String sql, UUID organizationId, Window window) {
        return jdbc.sql(sql).param("organization", organizationId).param("fromTime", window.from().atOffset(ZoneOffset.UTC))
                .param("toTime", window.to().atOffset(ZoneOffset.UTC));
    }
}
