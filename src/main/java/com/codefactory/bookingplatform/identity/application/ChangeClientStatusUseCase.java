package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.domain.port.ClientStatusChanges;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * HU-003 suspension and reactivation of a client by an administrator, each with its reason. A
 * suspended client cannot confirm bookings (HU-001).
 */
@Service
public class ChangeClientStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeClientStatusUseCase.class);

    private final ClientRepository clients;
    private final ClientStatusChanges statusChanges;

    public ChangeClientStatusUseCase(ClientRepository clients, ClientStatusChanges statusChanges) {
        this.clients = clients;
        this.statusChanges = statusChanges;
    }

    @Transactional
    public Client suspend(UUID clientId, String reason, UUID adminId) {
        return change(clientId, reason, adminId, Client::suspend);
    }

    @Transactional
    public Client reactivate(UUID clientId, String reason, UUID adminId) {
        return change(clientId, reason, adminId, Client::reactivate);
    }

    private Client change(UUID clientId, String reason, UUID adminId, Consumer<Client> transition) {
        Client client = clients.findById(clientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Client not found"));
        ClientStatus from = client.getStatus();
        transition.accept(client);
        // The aggregate lets a suspended client be suspended again; as an administrative act it would
        // record a change that changed nothing.
        if (client.getStatus() == from) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "The client is already " + from);
        }
        clients.save(client);
        statusChanges.record(clientId, from, client.getStatus(), reason.trim(), adminId);
        log.info("Client {} moved from {} to {} by {}", clientId, from, client.getStatus(), adminId);
        return client;
    }
}
