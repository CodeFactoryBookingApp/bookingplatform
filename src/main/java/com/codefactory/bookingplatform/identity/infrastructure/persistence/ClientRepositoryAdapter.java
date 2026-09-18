package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpaRepository;
    private final ClientMapper mapper;

    public ClientRepositoryAdapter(ClientJpaRepository jpaRepository, ClientMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Client> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByEmail(String email) {
        return jpaRepository.findByEmailIgnoreCase(email.toLowerCase(Locale.ROOT)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailIgnoreCase(email.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean existsByDocument(String document) {
        return jpaRepository.existsByDocumentIgnoreCase(document);
    }

    @Override
    public Client save(Client client) {
        ClientEntity entity = mapper.toEntity(client);
        return mapper.toDomain(jpaRepository.save(entity));
    }
}
