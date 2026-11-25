package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Bucket;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Group;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Metrics;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Period;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Report;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Service;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Window;
import com.codefactory.bookingplatform.booking.domain.port.ServiceDemandQueries;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-020 "Uso y demanda de servicios": who may read it, which periods are valid, how each service's
 * metrics, ranking, comparison with the previous period and high-cancellation alert are computed.
 *
 * <p>Techniques: equivalence partitioning over the role (OWNER, MANAGER allowed; STAFF denied),
 * boundary values on the period length (0, 1, 366, 367 days) and on the cancellation alert (exactly
 * 20 % is not flagged, just above 20 % is), and decision tables over which statuses count as revenue.
 */
class ServiceDemandUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 11, 15);
    private static final LocalDate TO = LocalDate.of(2026, 11, 22);
    /** Midnight in Bogota (UTC-5) of the bounds of the current and previous week. */
    private static final Window CURRENT = new Window(Instant.parse("2026-11-15T05:00:00Z"),
            Instant.parse("2026-11-22T05:00:00Z"));
    private static final Window PREVIOUS = new Window(Instant.parse("2026-11-08T05:00:00Z"),
            Instant.parse("2026-11-15T05:00:00Z"));

    private OrganizationRepository organizations;
    private ServiceDemandQueries queries;
    private ServiceDemandUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        queries = mock(ServiceDemandQueries.class);
        useCase = new ServiceDemandUseCase(new OrganizationAccess(organizations, mock(AuditActor.class)), queries);
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.of(MemberRole.MANAGER));
        when(queries.timezone(ORG)).thenReturn("America/Bogota");
    }

    private static Group group(UUID service, String name, BookingStatus status, long count, String currency,
                               String amount) {
        return new Group(service, name, status, currency, count, new BigDecimal(amount));
    }

    private void current(Group... rows) {
        when(queries.metrics(ORG, CURRENT)).thenReturn(List.of(rows));
    }

    private void previous(Group... rows) {
        when(queries.metrics(ORG, PREVIOUS)).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("HU-020 / ADR-0008: STAFF cannot read the demand report and nothing is queried")
    void staffCannotReadTheReport() {
        // Arrange
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.of(MemberRole.STAFF));

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.report(ORG, ACTOR, FROM, TO));

        // Assert
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
        verify(queries, never()).metrics(any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OWNER", "MANAGER"})
    @DisplayName("HU-020 / ADR-0008: the owner and the manager can read the demand report")
    void ownerAndManagerReadTheReport(MemberRole role) {
        // Arrange
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.of(role));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        assertEquals(new Period(FROM, TO), report.period());
        verify(queries).metrics(ORG, CURRENT);
    }

    @ParameterizedTest(name = "{0} to {1}")
    @CsvSource({
            "2026-11-15, 2026-11-15",
            "2026-11-16, 2026-11-15",
            "2025-01-01, 2026-01-03"
    })
    @DisplayName("HU-020: a period must cover between 1 and 366 days (the end is exclusive)")
    void invalidPeriodIsRejected(LocalDate from, LocalDate to) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.report(ORG, ACTOR, from, to));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        verify(queries, never()).metrics(any(), any());
    }

    @ParameterizedTest(name = "{0} days")
    @ValueSource(ints = {1, 366})
    @DisplayName("HU-020: 1 and 366 days are the shortest and longest valid periods; the previous period has the same length")
    void periodBoundsAreAccepted(int days) {
        // Arrange
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = from.plusDays(days);

        // Act
        Report report = useCase.report(ORG, ACTOR, from, to);

        // Assert
        assertEquals(new Period(from, to), report.period());
        assertEquals(new Period(from.minusDays(days), from), report.previousPeriod());
    }

    @Test
    @DisplayName("HU-020: periods are local days of the provider; the previous period is the same length right before")
    void periodsAreLocalDaysOfTheProvider() {
        // Arrange
        List<Bucket> demand = List.of(new Bucket(1, 9, 5));
        List<Bucket> before = List.of(new Bucket(7, 18, 1));
        when(queries.demand(ORG, CURRENT, "America/Bogota")).thenReturn(demand);
        when(queries.demand(ORG, PREVIOUS, "America/Bogota")).thenReturn(before);

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        assertEquals(new Period(LocalDate.of(2026, 11, 8), FROM), report.previousPeriod());
        assertEquals("America/Bogota", report.timezone());
        assertEquals(demand, report.demand());
        assertEquals(before, report.previousDemand());
    }

    @Test
    @DisplayName("HU-020: per service, all statuses count as created and the rates are over created bookings")
    void metricsPerService() {
        // Arrange
        UUID consulta = UUID.randomUUID();
        current(group(consulta, "Consulta", BookingStatus.COMPLETED, 1, "COP", "100"),
                group(consulta, "Consulta", BookingStatus.CANCELLED, 1, "COP", "150"),
                group(consulta, "Consulta", BookingStatus.NO_SHOW, 1, "COP", "160"),
                group(consulta, "Consulta", BookingStatus.CONFIRMED, 1, "USD", "200"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        Metrics metrics = report.services().get(0).current();
        assertEquals(new Metrics(4, 1, 1, 1, new BigDecimal("25.00"), new BigDecimal("25.00"),
                new BigDecimal("25.00"), Map.of("COP", new BigDecimal("100"), "USD", new BigDecimal("200"))), metrics);
    }

    @Test
    @DisplayName("HU-020: estimated revenue adds only completed and confirmed bookings, per currency")
    void revenueCountsOnlyCompletedAndConfirmed() {
        // Arrange
        UUID service = UUID.randomUUID();
        current(group(service, "Corte", BookingStatus.COMPLETED, 2, "COP", "90000"),
                group(service, "Corte", BookingStatus.CONFIRMED, 1, "COP", "45000"),
                group(service, "Corte", BookingStatus.RESERVED, 1, "COP", "45000"),
                group(service, "Corte", BookingStatus.CANCELLED, 1, "COP", "45000"),
                group(service, "Corte", BookingStatus.NO_SHOW, 1, "COP", "45000"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        assertEquals(Map.of("COP", new BigDecimal("135000")),
                report.services().get(0).current().estimatedRevenueByCurrency());
    }

    @ParameterizedTest(name = "{0} cancelled of {1} -> alert {2}")
    @CsvSource({
            "1, 5, false",
            "2, 10, false",
            "21, 100, true",
            "1, 4, true",
            "0, 0, false"
    })
    @DisplayName("HU-020: a service is flagged when its cancellations are above 20 % of its bookings, not at exactly 20 %")
    void highCancellationAlertBoundary(long cancelled, long created, boolean flagged) {
        // Arrange
        UUID service = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<Group> rows = new ArrayList<>();
        rows.add(group(other, "Otro", BookingStatus.COMPLETED, 1, "COP", "1"));
        if (cancelled > 0) {
            rows.add(group(service, "Corte", BookingStatus.CANCELLED, cancelled, "COP", "0"));
        }
        if (created > cancelled) {
            rows.add(group(service, "Corte", BookingStatus.COMPLETED, created - cancelled, "COP", "0"));
        }
        when(queries.metrics(ORG, CURRENT)).thenReturn(rows);
        previous(group(service, "Corte", BookingStatus.COMPLETED, 1, "COP", "0"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        Service corte = report.services().stream().filter(s -> s.id().equals(service)).findFirst().orElseThrow();
        assertEquals(flagged, corte.highCancellation());
    }

    @Test
    @DisplayName("HU-020: services are ranked by bookings created in the period, ties by name and then by id")
    void rankingByCreatedThenName() {
        // Arrange
        UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID top = UUID.randomUUID();
        UUID onlyBefore = UUID.randomUUID();
        current(group(b, "Barba", BookingStatus.COMPLETED, 2, "COP", "1"),
                group(top, "Tinte", BookingStatus.COMPLETED, 5, "COP", "1"),
                group(a, "Barba", BookingStatus.COMPLETED, 2, "COP", "1"),
                group(UUID.fromString("00000000-0000-0000-0000-00000000000c"), "Afeitado", BookingStatus.CANCELLED, 2,
                        "COP", "0"));
        previous(group(onlyBefore, "Masaje", BookingStatus.COMPLETED, 9, "COP", "1"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        assertEquals(List.of("Tinte", "Afeitado", "Barba", "Barba", "Masaje"),
                report.services().stream().map(Service::name).toList());
        assertEquals(a, report.services().get(2).id());
        assertEquals(b, report.services().get(3).id());
    }

    @Test
    @DisplayName("HU-020: a service with bookings only in the previous period appears with zero now and a -100 % variation")
    void serviceOnlyInThePreviousPeriod() {
        // Arrange
        UUID masaje = UUID.randomUUID();
        current();
        previous(group(masaje, "Masaje", BookingStatus.COMPLETED, 4, "COP", "400"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        Service service = report.services().get(0);
        assertEquals("Masaje", service.name());
        assertEquals(new Metrics(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.of()),
                service.current());
        assertEquals(4, service.previous().created());
        assertEquals(new BigDecimal("-100.00"), service.variation().createdPercent());
        assertEquals(new BigDecimal("-100.00"), service.variation().estimatedRevenuePercent().get("COP"));
        assertFalse(service.highCancellation());
    }

    @Test
    @DisplayName("HU-020: the comparison is relative to the previous period, and undefined (null) where it had nothing")
    void comparisonWithThePreviousPeriod() {
        // Arrange
        UUID consulta = UUID.randomUUID();
        current(group(consulta, "Consulta", BookingStatus.COMPLETED, 1, "COP", "100"),
                group(consulta, "Consulta", BookingStatus.CANCELLED, 1, "COP", "0"),
                group(consulta, "Consulta", BookingStatus.NO_SHOW, 1, "COP", "0"),
                group(consulta, "Consulta", BookingStatus.CONFIRMED, 1, "USD", "200"));
        previous(group(consulta, "Consulta", BookingStatus.COMPLETED, 2, "COP", "80"),
                group(consulta, "Consulta", BookingStatus.CANCELLED, 2, "COP", "0"),
                group(consulta, "Consulta", BookingStatus.NO_SHOW, 1, "COP", "0"));

        // Act
        Report report = useCase.report(ORG, ACTOR, FROM, TO);

        // Assert
        var variation = report.services().get(0).variation();
        assertEquals(new BigDecimal("-20.00"), variation.createdPercent());
        assertEquals(new BigDecimal("-50.00"), variation.completedPercent());
        assertEquals(new BigDecimal("-50.00"), variation.cancelledPercent());
        assertEquals(new BigDecimal("0.00"), variation.noShowPercent());
        assertEquals(new BigDecimal("-37.50"), variation.completionRatePercent());
        assertEquals(new BigDecimal("-37.50"), variation.cancellationRatePercent());
        assertEquals(new BigDecimal("25.00"), variation.noShowRatePercent());
        assertEquals(new BigDecimal("25.00"), variation.estimatedRevenuePercent().get("COP"));
        assertTrue(variation.estimatedRevenuePercent().containsKey("USD"));
        assertNull(variation.estimatedRevenuePercent().get("USD"));
    }
}
