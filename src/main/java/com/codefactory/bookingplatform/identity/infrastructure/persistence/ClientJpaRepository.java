package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDocumentIgnoreCase(String document);

    Optional<ClientEntity> findByEmailIgnoreCase(String email);
}
