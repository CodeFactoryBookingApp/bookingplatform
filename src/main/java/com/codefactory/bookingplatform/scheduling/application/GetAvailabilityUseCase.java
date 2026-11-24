package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailabilityCalculator;
import com.codefactory.bookingplatform.scheduling.domain.model.AvailableSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
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
 * HU-008 "Generación de franjas" + HU-011 "Calendario de disponibilidad": las franjas libres de
 * una agenda en un rango de fechas, para cualquier miembro del proveedor. Reutiliza HU-006
 * (Agenda), HU-007 (AgendaException) y HU-009 (los mantenimientos del recurso, si la agenda es de
 * uno), y descuenta las reservas ya hechas (AgendaBookings).
 */
@Service
public class GetAvailabilityUseCase {

    private static final int MAX_DAYS = 62;

    private final AgendaRepository agendas;
    private final AgendaExceptionRepository exceptions;
    private final AgendaBookings bookings;
    private final CatalogLookup catalog;
    private final OrganizationAccess access;

    public GetAvailabilityUseCase(AgendaRepository agendas, AgendaExceptionRepository exceptions,
                                  AgendaBookings bookings, CatalogLookup catalog, OrganizationAccess access) {
        this.agendas = agendas;
        this.exceptions = exceptions;
        this.bookings = bookings;
        this.catalog = catalog;
        this.access = access;
    }

    public record DayAvailability(LocalDate date, List<AvailableSlot> slots) {
    }

    @Transactional(readOnly = true)
    public List<DayAvailability> calendar(UUID organizationId, UUID userId, UUID agendaId, LocalDate from,
                                          LocalDate to) {
        access.requireMember(organizationId, userId);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The range must not be inverted and must cover at most " + MAX_DAYS + " days");
        }
        Agenda agenda = agendas.findById(organizationId, agendaId).orElseThrow(ManageAgendaUseCase::notFound);
        ZoneId zone = access.locationZone(organizationId, agenda.getLocationId());

        Map<LocalDate, AgendaException> byDate = new LinkedHashMap<>();
        for (AgendaException exception : exceptions.findByAgendaAndDateRange(agendaId, from, to)) {
            byDate.put(exception.date(), exception);
        }
        List<MaintenanceWindow> maintenances = agenda.getResourceId() == null
                ? List.of()
                : catalog.resource(organizationId, agenda.getResourceId()).map(Resource::getMaintenances).orElse(List.of());

        List<BookedSlot> booked = bookings.between(agenda, from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());

        List<DayAvailability> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<AvailableSlot> slots = AvailabilityCalculator.freeSlotsFor(agenda, date, zone, byDate.get(date),
                    maintenances, booked);
            result.add(new DayAvailability(date, slots));
        }
        return result;
    }
}