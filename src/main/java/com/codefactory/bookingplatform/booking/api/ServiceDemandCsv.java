package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Bucket;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Metrics;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Report;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Variation;
import com.codefactory.bookingplatform.shared.export.Csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ServiceDemandCsv {

    private ServiceDemandCsv() {
    }

    static String format(Report report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("periodo", "actual", "", "desde", "", report.period().from()));
        rows.add(row("periodo", "actual", "", "hasta_exclusivo", "", report.period().to()));
        rows.add(row("periodo", "anterior", "", "desde", "", report.previousPeriod().from()));
        rows.add(row("periodo", "anterior", "", "hasta_exclusivo", "", report.previousPeriod().to()));
        rows.add(row("periodo", "", "", "zona_horaria", "", report.timezone()));
        for (int i = 0; i < report.services().size(); i++) {
            var service = report.services().get(i);
            rows.add(row("ranking", "actual", service.name(), "posicion", "", i + 1));
            rows.add(row("metricas", "actual", service.name(), "alta_cancelacion", "", service.highCancellation()));
            metrics(rows, "actual", service.name(), service.current());
            metrics(rows, "anterior", service.name(), service.previous());
            variation(rows, service.name(), service.variation());
        }
        demand(rows, "actual", report.demand());
        demand(rows, "anterior", report.previousDemand());
        return Csv.of(List.of("seccion", "periodo", "servicio", "metrica", "moneda", "dia_iso", "hora", "valor"), rows);
    }

    private static void metrics(List<List<String>> rows, String period, String service, Metrics metrics) {
        rows.add(row("metricas", period, service, "creadas", "", metrics.created()));
        rows.add(row("metricas", period, service, "completadas", "", metrics.completed()));
        rows.add(row("metricas", period, service, "canceladas", "", metrics.cancelled()));
        rows.add(row("metricas", period, service, "inasistencias", "", metrics.noShow()));
        rows.add(row("metricas", period, service, "tasa_completadas_pct", "", metrics.completionRatePercent()));
        rows.add(row("metricas", period, service, "tasa_canceladas_pct", "", metrics.cancellationRatePercent()));
        rows.add(row("metricas", period, service, "tasa_inasistencias_pct", "", metrics.noShowRatePercent()));
        metrics.estimatedRevenueByCurrency().forEach((currency, amount) ->
                rows.add(row("metricas", period, service, "ingreso_estimado", currency, amount)));
    }

    private static void variation(List<List<String>> rows, String service, Variation change) {
        rows.add(row("variacion_pct", "", service, "creadas", "", change.createdPercent()));
        rows.add(row("variacion_pct", "", service, "completadas", "", change.completedPercent()));
        rows.add(row("variacion_pct", "", service, "canceladas", "", change.cancelledPercent()));
        rows.add(row("variacion_pct", "", service, "inasistencias", "", change.noShowPercent()));
        rows.add(row("variacion_pct", "", service, "tasa_completadas_pct", "", change.completionRatePercent()));
        rows.add(row("variacion_pct", "", service, "tasa_canceladas_pct", "", change.cancellationRatePercent()));
        rows.add(row("variacion_pct", "", service, "tasa_inasistencias_pct", "", change.noShowRatePercent()));
        change.estimatedRevenuePercent().forEach((currency, amount) ->
                rows.add(row("variacion_pct", "", service, "ingreso_estimado", currency, amount)));
    }

    private static void demand(List<List<String>> rows, String period, List<Bucket> buckets) {
        for (Bucket bucket : buckets) {
            rows.add(List.of("demanda", period, "", "reservas", "", Integer.toString(bucket.dayOfWeek()),
                    Integer.toString(bucket.hour()), Long.toString(bucket.bookings())));
        }
    }

    private static List<String> row(String section, String period, String service, String metric, String currency,
                                    Object value) {
        return List.of(section, period, service, metric, currency, "", "", Objects.toString(value, ""));
    }
}
