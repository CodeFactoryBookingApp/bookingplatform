package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceBookings;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.catalog.domain.service.ResourceAllocator;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HU-010 "Asignación de recursos": para una reserva de un servicio, en una sede y un rango de
 * tiempo, elige qué recursos concretos la cubren a partir de lo que el servicio exige (HU-004) y
 * de cuáles están realmente libres (ni en mantenimiento — HU-009 — ni ya reservados).
 *
 * <p><b>Contrato con el ciclo de vida de la reserva (HU-012, en feature/booking-lifecycle):</b>
 * esto sólo ELIGE los recursos; no los reserva. Quien crea la reserva inserta las filas de
 * {@code booking_resources} con los ids de {@link Assignment#allResourceIds()}, dentro de su
 * propia transacción. El EXCLUDE de la base de datos sobre {@code booking_resources} (V1) es la
 * última defensa contra dos reservas que, por una condición de carrera, eligieron el mismo
 * recurso para el mismo instante: si el insert falla por esa restricción, quien reserva debe
 * volver a llamar a {@code assign} y reintentar, no asumir que la elección sigue siendo válida.
 *
 * <p><b>NO SE USA.</b> Ninguna clase llama a este caso de uso. La asignación de recursos de las
 * reservas ya la hace otra implementación: la consulta {@code booking/available-slots.sql}
 * (bloques {@code eligible_resources} y {@code allocations}), que usa
 * {@code BookingReservationAdapter} al consultar disponibilidad y al crear la reserva (HU-012).
 * Esa versión además respeta preparación y limpieza, agendas con recurso fijo y sesiones grupales.
 * Se conserva sin conectar hasta que el equipo decida si se elimina; cambiar esta clase no altera
 * cómo se asignan los recursos.
 */
@Service
public class AssignResourcesUseCase {

    private final ServiceRepository services;
    private final ResourceRepository resources;
    private final ResourceBookings bookings;

    public AssignResourcesUseCase(ServiceRepository services, ResourceRepository resources, ResourceBookings bookings) {
        this.services = services;
        this.resources = resources;
        this.bookings = bookings;
    }

    /** resourceTypeId -> the resources assigned to cover its requirement. */
    public record Assignment(Map<UUID, List<UUID>> byType) {

        public List<UUID> allResourceIds() {
            return byType.values().stream().flatMap(List::stream).toList();
        }
    }

    @Transactional(readOnly = true)
    public Assignment assign(UUID organizationId, UUID serviceId, UUID locationId, Instant startsAt, Instant endsAt) {
        OfferedService service = services.findById(organizationId, serviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Service not found"));

        Map<UUID, List<Resource>> freeByType = new LinkedHashMap<>();
        for (ResourceRequirement requirement : service.getRequirements()) {
            List<Resource> candidates = resources.findActive(organizationId, locationId, requirement.resourceTypeId());
            Set<UUID> busy = bookings.busyBetween(candidates.stream().map(Resource::getId).toList(), startsAt, endsAt);
            List<Resource> free = candidates.stream()
                    .filter(resource -> resource.isAvailableDuring(startsAt, endsAt))
                    .filter(resource -> !busy.contains(resource.getId()))
                    .toList();
            freeByType.put(requirement.resourceTypeId(), free);
        }
        return new Assignment(ResourceAllocator.assign(service.getRequirements(), freeByType));
    }
}