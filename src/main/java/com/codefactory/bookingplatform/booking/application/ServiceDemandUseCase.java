package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingRates;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Group;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Metrics;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Period;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Report;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Variation;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Window;
import com.codefactory.bookingplatform.booking.domain.port.ServiceDemandQueries;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static com.codefactory.bookingplatform.booking.domain.model.BookingStatus.CANCELLED;
import static com.codefactory.bookingplatform.booking.domain.model.BookingStatus.COMPLETED;
import static com.codefactory.bookingplatform.booking.domain.model.BookingStatus.CONFIRMED;
import static com.codefactory.bookingplatform.booking.domain.model.BookingStatus.NO_SHOW;

@Service
public class ServiceDemandUseCase {

    private static final BigDecimal HIGH_CANCELLATION = BigDecimal.valueOf(20);

    private final OrganizationAccess access;
    private final ServiceDemandQueries queries;

    public ServiceDemandUseCase(OrganizationAccess access, ServiceDemandQueries queries) {
        this.access = access;
        this.queries = queries;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report report(UUID organization, UUID actor, LocalDate from, LocalDate to) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER);
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 1 || days > 366 || from.getYear() < 2 || to.getYear() > 9999) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Report period must cover between 1 and 366 days");
        }
        ZoneId zone = ZoneId.of(queries.timezone(organization));
        Period period = new Period(from, to);
        Period previous = new Period(from.minusDays(days), from);
        Window currentWindow = window(period, zone);
        Window previousWindow = window(previous, zone);
        Map<UUID, Totals> current = group(queries.metrics(organization, currentWindow));
        Map<UUID, Totals> before = group(queries.metrics(organization, previousWindow));
        var services = Stream.concat(current.keySet().stream(), before.keySet().stream()).distinct()
                .map(id -> service(id, current.get(id), before.get(id)))
                .sorted(Comparator.comparingLong((ServiceDemand.Service service) -> service.current().created())
                        .reversed().thenComparing(ServiceDemand.Service::name).thenComparing(ServiceDemand.Service::id))
                .toList();
        return new Report(period, previous, zone.getId(), services, queries.demand(organization, currentWindow, zone.getId()),
                queries.demand(organization, previousWindow, zone.getId()));
    }

    private static Window window(Period period, ZoneId zone) {
        return new Window(period.from().atStartOfDay(zone).toInstant(), period.to().atStartOfDay(zone).toInstant());
    }

    private static Map<UUID, Totals> group(List<Group> rows) {
        Map<UUID, Totals> totals = new LinkedHashMap<>();
        for (Group row : rows) {
            totals.computeIfAbsent(row.serviceId(), ignored -> new Totals(row.serviceName())).add(row);
        }
        return totals;
    }

    private static ServiceDemand.Service service(UUID id, Totals current, Totals previous) {
        String name = current == null ? previous.name : current.name;
        Metrics currentMetrics = current == null ? new Totals(name).metrics() : current.metrics();
        Metrics previousMetrics = previous == null ? new Totals(name).metrics() : previous.metrics();
        boolean flagged = BigDecimal.valueOf(currentMetrics.cancelled()).movePointRight(2)
                .compareTo(BigDecimal.valueOf(currentMetrics.created()).multiply(HIGH_CANCELLATION)) > 0;
        return new ServiceDemand.Service(id, name,
                currentMetrics, previousMetrics, variation(currentMetrics, previousMetrics), flagged);
    }

    private static Variation variation(Metrics current, Metrics previous) {
        Map<String, BigDecimal> revenue = new TreeMap<>();
        Map<String, BigDecimal> currentRevenue = current.estimatedRevenueByCurrency();
        Map<String, BigDecimal> previousRevenue = previous.estimatedRevenueByCurrency();
        Stream.concat(currentRevenue.keySet().stream(), previousRevenue.keySet().stream()).distinct()
                .forEach(currency -> revenue.put(currency, BookingRates.variation(
                        currentRevenue.getOrDefault(currency, BigDecimal.ZERO),
                        previousRevenue.getOrDefault(currency, BigDecimal.ZERO))));
        return new Variation(variation(current.created(), previous.created()),
                variation(current.completed(), previous.completed()), variation(current.cancelled(), previous.cancelled()),
                variation(current.noShow(), previous.noShow()),
                BookingRates.variation(current.completionRatePercent(), previous.completionRatePercent()),
                BookingRates.variation(current.cancellationRatePercent(), previous.cancellationRatePercent()),
                BookingRates.variation(current.noShowRatePercent(), previous.noShowRatePercent()), revenue);
    }

    private static BigDecimal variation(long current, long previous) {
        return BookingRates.variation(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private static final class Totals {
        private final String name;
        private final Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        private final Map<String, BigDecimal> revenue = new TreeMap<>();
        private long created;

        private Totals(String name) {
            this.name = name;
        }

        private void add(Group row) {
            counts.merge(row.status(), row.count(), Long::sum);
            created += row.count();
            if (row.status() == COMPLETED || row.status() == CONFIRMED) {
                revenue.merge(row.currency(), row.amount(), BigDecimal::add);
            }
        }

        private Metrics metrics() {
            long completed = counts.getOrDefault(COMPLETED, 0L);
            long cancelled = counts.getOrDefault(CANCELLED, 0L);
            long noShow = counts.getOrDefault(NO_SHOW, 0L);
            return new Metrics(created, completed, cancelled, noShow, BookingRates.percentage(completed, created),
                    BookingRates.percentage(cancelled, created), BookingRates.percentage(noShow, created), revenue);
        }
    }
}
