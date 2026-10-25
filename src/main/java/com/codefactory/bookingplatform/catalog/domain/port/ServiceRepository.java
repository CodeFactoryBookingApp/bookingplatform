package com.codefactory.bookingplatform.catalog.domain.port;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository {

    /** Stores the service with its locations and requirements. @throws DuplicateNameException if the provider already has one with that name */
    void saveNew(OfferedService service);

    /** Stores the editable fields and the status. @throws DuplicateNameException on a rename to a name in use */
    void save(OfferedService service);

    List<OfferedService> findAll(UUID organizationId);

    /** Only within the organization: a service of another provider is not found. */
    Optional<OfferedService> findById(UUID organizationId, UUID serviceId);

    void delete(UUID serviceId);
}
