package com.codefactory.bookingplatform.identity.domain.port;

import com.codefactory.bookingplatform.identity.domain.model.Client;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {

    Optional<Client> findById(UUID id);

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDocument(String document);

    Client save(Client client);
}
