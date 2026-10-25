package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.DuplicateClientException;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            // The id is assigned, so a plain save() defers the INSERT to the commit, after the
            // use case has returned and can no longer compensate the identity provider (D7).
            return mapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    /** uk_clients_email, ux_clients_email_lower and uk_clients_document, from V1. */
    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
        if (constraint == null) {
            return ex;
        }
        if (constraint.contains("email")) {
            return new DuplicateClientException(DuplicateClientException.Field.EMAIL, ex);
        }
        if (constraint.contains("document")) {
            return new DuplicateClientException(DuplicateClientException.Field.DOCUMENT, ex);
        }
        return ex;
    }
}
