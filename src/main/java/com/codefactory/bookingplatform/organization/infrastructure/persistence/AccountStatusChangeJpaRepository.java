package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountStatusChangeJpaRepository extends JpaRepository<AccountStatusChangeEntity, UUID> {
}
