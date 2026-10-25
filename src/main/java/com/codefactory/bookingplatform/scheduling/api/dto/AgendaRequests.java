package com.codefactory.bookingplatform.scheduling.api.dto;

import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request bodies of HU-006. */
public final class AgendaRequests {

    private AgendaRequests() {
    }

    @Schema(description = "Opening hours on one day of the week, in the local time of the location")
    public record Block(

            @NotNull(message = "dayOfWeek is required")
            @Schema(example = "MONDAY")
            DayOfWeek dayOfWeek,

            @NotNull(message = "startTime is required")
            @Schema(type = "string", example = "08:00")
            LocalTime startTime,

            @NotNull(message = "endTime is required")
            @Schema(type = "string", example = "12:00")
            LocalTime endTime) {

        TimeBlock toDomain() {
            return new TimeBlock(dayOfWeek, startTime, endTime);
        }
    }

    static List<TimeBlock> toDomain(List<Block> blocks) {
        return blocks.stream().map(Block::toDomain).toList();
    }

    @Schema(description = "An agenda of a location, or of one resource in it (HU-006)")
    public record CreateAgenda(

            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @NotNull(message = "locationId is required")
            UUID locationId,

            @Schema(description = "Optional: the agenda of one resource of the location")
            UUID resourceId,

            @NotNull(message = "slotMinutes is required")
            @Schema(description = "Granularity of the slots: 15, 30 or 60", example = "30")
            Integer slotMinutes,

            @NotNull(message = "validFrom is required")
            LocalDate validFrom,

            @Schema(description = "Optional: without it the agenda has no end")
            LocalDate validUntil,

            @NotEmpty(message = "at least one service is required")
            @Size(max = 50, message = "at most 50 services")
            List<@NotNull(message = "a service id cannot be null") UUID> serviceIds,

            @NotEmpty(message = "at least one time block is required")
            @Size(max = 100, message = "at most 100 time blocks")
            List<@Valid @NotNull(message = "a block cannot be null") Block> blocks) {

        public List<TimeBlock> blocksToDomain() {
            return toDomain(blocks);
        }
    }

    public record ReplaceBlocks(

            @NotEmpty(message = "at least one time block is required")
            @Size(max = 100, message = "at most 100 time blocks")
            List<@Valid @NotNull(message = "a block cannot be null") Block> blocks) {

        public List<TimeBlock> blocksToDomain() {
            return toDomain(blocks);
        }
    }

    @Schema(description = "Give the target days exactly the blocks of the source day (HU-006)")
    public record CopyDay(

            @NotNull(message = "fromDay is required")
            DayOfWeek fromDay,

            @NotEmpty(message = "at least one target day is required")
            Set<@NotNull(message = "a day cannot be null") DayOfWeek> toDays) {
    }

    public record ChangeGranularity(

            @NotNull(message = "slotMinutes is required")
            @Schema(description = "15, 30 or 60", example = "15")
            Integer slotMinutes) {
    }
}
