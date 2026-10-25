package com.codefactory.bookingplatform.scheduling.domain.port;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaConflictException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaRepository {

    /**
     * Stores the agenda with its services and blocks.
     *
     * @throws AgendaConflictException if the name is in use, or another agenda of the same location
     *                                 or resource is valid in an overlapping period
     */
    void saveNew(Agenda agenda);

    /** Replaces the blocks of the agenda with its current ones. */
    void saveBlocks(Agenda agenda);

    void saveGranularity(Agenda agenda);

    List<Agenda> findAll(UUID organizationId);

    /** Only within the organization: an agenda of another provider is not found. */
    Optional<Agenda> findById(UUID organizationId, UUID agendaId);
}
