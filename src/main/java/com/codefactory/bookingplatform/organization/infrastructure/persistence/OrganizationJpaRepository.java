package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, UUID> {

    boolean existsByIdentificationTypeAndIdentificationNumber(IdentificationType type, String number);
}
