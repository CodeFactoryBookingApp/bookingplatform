package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailabilityCalculator;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailableSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.OccupancyReport;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaExceptionRepository;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-019 "Reporte de ocupación": para cada agenda del proveedor, qué fracción de sus franjas
 * posibles en el periodo terminó reservada. Reutiliza las agendas (HU-006), sus excepciones
 * (HU-007) y los tiempos ya ocupados (motor de HU-008). Visible sólo para el owner o un manager,
 * igual que el resto de reportes (ServiceDemandUseCase).
 */
@Service
public class OccupancyReportUseCase {

    private static final int MAX_DAYS = 366;

    private final AgendaRepository agendas;
    private final AgendaExceptionRepository exceptions;
    private final AgendaBookings bookings;
    private final CatalogLookup catalog;
    private final OrganizationAccess access;

    public OccupancyReportUseCase(AgendaRepository agendas, AgendaExceptionRepository exceptions,
                                  AgendaBookings bookings, CatalogLookup catalog, OrganizationAccess access) {
        this.agendas = agendas;
        this.exceptions = exceptions;
        this.bookings = bookings;
        this.catalog = catalog;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<OccupancyReport> report(UUID organizationId, UUID userId, LocalDate from, LocalDate to) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0 || days > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The report period must not be inverted and must cover at most " + MAX_DAYS + " days");
        }
        List<OccupancyReport> report = new ArrayList<>();
        for (Agenda agenda : agendas.findAll(organizationId)) {
            report.add(occupancyOf(organizationId, agenda, from, to));
        }
        return report;
    }

    private OccupancyReport occupancyOf(UUID organizationId, Agenda agenda, LocalDate from, LocalDate to) {
        ZoneId zone = access.locationZone(organizationId, agenda.getLocationId());

        Map<LocalDate, AgendaException> byDate = new LinkedHashMap<>();
        for (AgendaException exception : exceptions.findByAgendaAndDateRange(agenda.getId(), from, to)) {
            byDate.put(exception.date(), exception);
        }
        List<MaintenanceWindow> maintenances = agenda.getResourceId() == null
                ? List.of()
                : catalog.resource(organizationId, agenda.getResourceId()).map(Resource::getMaintenances).orElse(List.of());

        List<BookedSlot> booked = bookings.between(agenda, from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());

        long total = 0;
        long occupied = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<AvailableSlot> possible = AvailabilityCalculator.possibleSlots(agenda, date, zone, byDate.get(date),
                    maintenances);
            List<AvailableSlot> free = AvailabilityCalculator.freeSlots(possible, booked);
            total += possible.size();
            occupied += possible.size() - free.size();
        }
        return new OccupancyReport(agenda.getId(), agenda.getName(), from, to, total, occupied);
    }
}