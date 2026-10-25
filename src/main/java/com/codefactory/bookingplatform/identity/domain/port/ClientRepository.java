package com.codefactory.bookingplatform.identity.domain.port;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.DuplicateClientException;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {

    Optional<Client> findById(UUID id);

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDocument(String document);

    /**
     * Writes the client before returning, not at commit, so a caller that has to undo work done
     * elsewhere still sees the failure.
     *
     * @throws DuplicateClientException if another client already holds the email or the document
     */
    Client save(Client client);
}
