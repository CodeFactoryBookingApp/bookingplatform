package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Bucket;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Metrics;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Period;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Report;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Service;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Variation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HU-020 export: the CSV carries the periods, each service's ranking position, alert, metrics of
 * both periods and variation, and the hourly demand of both periods, one value per row.
 *
 * <p>Equivalence partitioning over the values: present, undefined (null variation) and per currency.
 */
class ServiceDemandCsvTest {

    private static final Metrics EMPTY = new Metrics(0, 0, 0, 0, new BigDecimal("0"), new BigDecimal("0"),
            new BigDecimal("0"), Map.of());

    private static Report report(List<Service> services, List<Bucket> demand, List<Bucket> previousDemand) {
        return new Report(new Period(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 22)),
                new Period(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15)), "America/Bogota", services, demand,
                previousDemand);
    }

    private static Variation undefined() {
        return new Variation(null, null, null, null, null, null, null, Map.of());
    }

    private static List<String> lines(String csv) {
        return Arrays.asList(csv.substring(1).split("\r\n"));
    }

    @Test
    @DisplayName("HU-020: the export opens with the header and both periods, the end marked as exclusive, and the zone")
    void headerAndPeriods() {
        // Act
        String csv = ServiceDemandCsv.format(report(List.of(), List.of(), List.of()));

        // Assert
        assertEquals(List.of(
                "seccion,periodo,servicio,metrica,moneda,dia_iso,hora,valor",
                "periodo,actual,,desde,,,,2026-11-15",
                "periodo,actual,,hasta_exclusivo,,,,2026-11-22",
                "periodo,anterior,,desde,,,,2026-11-08",
                "periodo,anterior,,hasta_exclusivo,,,,2026-11-15",
                "periodo,,,zona_horaria,,,,America/Bogota"), lines(csv));
    }

    @Test
    @DisplayName("HU-020: each service gets its ranking position (1-based, in report order), its alert, its metrics per period and an empty value where the variation is undefined")
    void servicesInRankingOrder() {
        // Arrange
        Metrics current = new Metrics(4, 1, 1, 1, new BigDecimal("25.00"), new BigDecimal("25.00"),
                new BigDecimal("25.00"), Map.of("COP", new BigDecimal("100")));
        Variation variation = new Variation(new BigDecimal("300.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
                new BigDecimal("15.00"), new BigDecimal("75.00"), new BigDecimal("30.00"), new BigDecimal("40.00"),
                Map.of("COP", new BigDecimal("25.00")));
        Service consulta = new Service(UUID.randomUUID(), "Consulta", current, EMPTY, variation, true);
        Service seguimiento = new Service(UUID.randomUUID(), "Seguimiento", EMPTY, EMPTY, undefined(), false);

        // Act
        String csv = ServiceDemandCsv.format(report(List.of(consulta, seguimiento), List.of(), List.of()));

        // Assert
        List<String> lines = lines(csv);
        assertEquals(List.of(
                "ranking,actual,Consulta,posicion,,,,1",
                "metricas,actual,Consulta,alta_cancelacion,,,,true",
                "metricas,actual,Consulta,creadas,,,,4",
                "metricas,actual,Consulta,completadas,,,,1",
                "metricas,actual,Consulta,canceladas,,,,1",
                "metricas,actual,Consulta,inasistencias,,,,1",
                "metricas,actual,Consulta,tasa_completadas_pct,,,,25.00",
                "metricas,actual,Consulta,tasa_canceladas_pct,,,,25.00",
                "metricas,actual,Consulta,tasa_inasistencias_pct,,,,25.00",
                "metricas,actual,Consulta,ingreso_estimado,COP,,,100",
                "metricas,anterior,Consulta,creadas,,,,0",
                "metricas,anterior,Consulta,completadas,,,,0",
                "metricas,anterior,Consulta,canceladas,,,,0",
                "metricas,anterior,Consulta,inasistencias,,,,0",
                "metricas,anterior,Consulta,tasa_completadas_pct,,,,0",
                "metricas,anterior,Consulta,tasa_canceladas_pct,,,,0",
                "metricas,anterior,Consulta,tasa_inasistencias_pct,,,,0",
                "variacion_pct,,Consulta,creadas,,,,300.00",
                "variacion_pct,,Consulta,completadas,,,,10.00",
                "variacion_pct,,Consulta,canceladas,,,,20.00",
                "variacion_pct,,Consulta,inasistencias,,,,15.00",
                "variacion_pct,,Consulta,tasa_completadas_pct,,,,75.00",
                "variacion_pct,,Consulta,tasa_canceladas_pct,,,,30.00",
                "variacion_pct,,Consulta,tasa_inasistencias_pct,,,,40.00",
                "variacion_pct,,Consulta,ingreso_estimado,COP,,,25.00",
                "ranking,actual,Seguimiento,posicion,,,,2",
                "metricas,actual,Seguimiento,alta_cancelacion,,,,false"), lines.subList(6, 33));
        assertEquals("variacion_pct,,Seguimiento,creadas,,,,", lines.get(47));
    }

    @Test
    @DisplayName("HU-020: the hourly demand of both periods is exported with ISO weekday and hour")
    void demandOfBothPeriods() {
        // Act
        String csv = ServiceDemandCsv.format(report(List.of(), List.of(new Bucket(1, 9, 5), new Bucket(5, 17, 2)),
                List.of(new Bucket(7, 8, 1))));

        // Assert
        assertEquals(List.of(
                "demanda,actual,,reservas,,1,9,5",
                "demanda,actual,,reservas,,5,17,2",
                "demanda,anterior,,reservas,,7,8,1"), lines(csv).subList(6, 9));
    }

    @Test
    @DisplayName("HU-020: a service name with a comma stays in one quoted field")
    void serviceNameWithCommaIsQuoted() {
        // Arrange
        Service service = new Service(UUID.randomUUID(), "Corte, barba", EMPTY, EMPTY, undefined(), false);

        // Act
        String csv = ServiceDemandCsv.format(report(List.of(service), List.of(), List.of()));

        // Assert
        assertEquals("ranking,actual,\"Corte, barba\",posicion,,,,1", lines(csv).get(6));
    }
}
