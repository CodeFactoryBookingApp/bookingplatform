package com.codefactory.bookingplatform.identity.infrastructure.mapper;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import org.mapstruct.Mapper;

@Mapper
public interface ClientMapper {

    ClientEntity toEntity(Client client);

    Client toDomain(ClientEntity entity);
}
