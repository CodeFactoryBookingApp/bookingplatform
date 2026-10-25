package com.codefactory.bookingplatform.scheduling.api.dto;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AgendaResponse(UUID id, String name, UUID locationId, UUID resourceId, int slotMinutes,
                             LocalDate validFrom, LocalDate validUntil, List<UUID> serviceIds,
                             List<BlockResponse> blocks) {

    public record BlockResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

        static BlockResponse from(TimeBlock block) {
            return new BlockResponse(block.day(), block.start(), block.end());
        }
    }

    public static AgendaResponse from(Agenda agenda) {
        return new AgendaResponse(agenda.getId(), agenda.getName(), agenda.getLocationId(), agenda.getResourceId(),
                agenda.getSlotMinutes(), agenda.getValidFrom(), agenda.getValidUntil(), agenda.getServiceIds(),
                agenda.getBlocks().stream().map(BlockResponse::from).toList());
    }
}
