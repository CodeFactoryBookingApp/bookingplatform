package com.codefactory.bookingplatform.catalog.domain.service;

import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-010: entre los recursos libres de cada tipo, elige cuáles cubren lo que el servicio exige
 * (HU-004). Función pura; "libre" ya viene decidido por quien llama (HU-009 + reservas ya hechas).
 *
 * <p><b>NO SE USA.</b> Solo la llama {@code AssignResourcesUseCase}, que a su vez no usa nadie. La
 * asignación real de recursos de las reservas la hace {@code booking/available-slots.sql}
 * (bloques {@code eligible_resources} y {@code allocations}, HU-012). Se conserva sin conectar
 * hasta que el equipo decida si se elimina; cambiar esta clase no altera cómo se asignan los recursos.
 */
public final class ResourceAllocator {

    private ResourceAllocator() {
    }

    public static Map<UUID, List<UUID>> assign(List<ResourceRequirement> requirements,
                                                Map<UUID, List<Resource>> freeResourcesByType) {
        Map<UUID, List<UUID>> assigned = new LinkedHashMap<>();
        Map<String, String> shortfalls = new LinkedHashMap<>();
        for (ResourceRequirement requirement : requirements) {
            List<Resource> free = freeResourcesByType.getOrDefault(requirement.resourceTypeId(), List.of());
            if (free.size() < requirement.quantity()) {
                shortfalls.put(requirement.resourceTypeId().toString(),
                        "requires " + requirement.quantity() + ", " + free.size() + " free in that time slot");
                continue;
            }
            assigned.put(requirement.resourceTypeId(),
                    free.stream().limit(requirement.quantity()).map(Resource::getId).toList());
        }
        if (!shortfalls.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RESOURCES,
                    "Not enough free resources for this service in that time slot", shortfalls);
        }
        return assigned;
    }
}