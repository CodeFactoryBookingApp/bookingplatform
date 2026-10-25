package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * services through JPA; service_locations and service_resource_requirements, two plain link
 * tables with composite keys, through JDBC in the same transaction. Both carry organization_id,
 * so their composite foreign keys reject a location or a type of another provider (V1).
 */
@Repository
public class ServiceRepositoryAdapter implements ServiceRepository {

    /** uk_services_org_name, from V1. */
    private static final String NAME_CONSTRAINT = "_name";

    private final ServiceJpaRepository services;
    private final JdbcTemplate jdbc;

    public ServiceRepositoryAdapter(ServiceJpaRepository services, JdbcTemplate jdbc) {
        this.services = services;
        this.jdbc = jdbc;
    }

    @Override
    public void saveNew(OfferedService service) {
        ServiceEntity entity = new ServiceEntity();
        entity.setId(service.getId());
        entity.setOrganizationId(service.getOrganizationId());
        entity.setCurrencyId(currencyId());
        copy(service, entity);
        flush(entity);
        for (UUID locationId : service.getLocationIds()) {
            jdbc.update("insert into service_locations (service_id, location_id, organization_id) values (?, ?, ?)",
                    service.getId(), locationId, service.getOrganizationId());
        }
        for (ResourceRequirement requirement : service.getRequirements()) {
            jdbc.update("""
                    insert into service_resource_requirements (id, service_id, resource_type_id, organization_id,
                                                               quantity_required)
                    values (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), service.getId(), requirement.resourceTypeId(),
                    service.getOrganizationId(), requirement.quantity());
        }
    }

    @Override
    public void save(OfferedService service) {
        ServiceEntity entity = services.findById(service.getId()).orElseThrow();
        copy(service, entity);
        flush(entity);
    }

    @Override
    public List<OfferedService> findAll(UUID organizationId) {
        return services.findByOrganizationIdOrderByName(organizationId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<OfferedService> findById(UUID organizationId, UUID serviceId) {
        return services.findByIdAndOrganizationId(serviceId, organizationId).map(this::toDomain);
    }

    /** service_locations and service_resource_requirements go with it: on delete cascade (V1). */
    @Override
    public void delete(UUID serviceId) {
        services.deleteById(serviceId);
        services.flush();
    }

    private void flush(ServiceEntity entity) {
        try {
            services.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    /** COP is the only currency of the MVP; V5 loads it in every database. */
    private UUID currencyId() {
        return jdbc.queryForObject("select id from currencies where code = ?", UUID.class, OfferedService.CURRENCY);
    }

    private static void copy(OfferedService service, ServiceEntity entity) {
        OfferedService.Details details = service.details();
        entity.setName(details.name());
        entity.setDescription(details.description());
        entity.setDurationMinutes(details.durationMinutes());
        entity.setPreparationMinutes(details.preparationMinutes());
        entity.setCleanupMinutes(details.cleanupMinutes());
        entity.setPrice(details.price());
        entity.setCustomerCapacity(details.customerCapacity());
        entity.setStatus(service.getStatus());
    }

    private OfferedService toDomain(ServiceEntity entity) {
        List<UUID> locationIds = jdbc.queryForList(
                "select location_id from service_locations where service_id = ? order by location_id",
                UUID.class, entity.getId());
        List<ResourceRequirement> requirements = jdbc.query("""
                select resource_type_id, quantity_required from service_resource_requirements
                 where service_id = ? order by resource_type_id
                """, (row, n) -> new ResourceRequirement(row.getObject("resource_type_id", UUID.class),
                row.getInt("quantity_required")), entity.getId());
        return new OfferedService(entity.getId(), entity.getOrganizationId(),
                new OfferedService.Details(entity.getName(), entity.getDescription(), entity.getDurationMinutes(),
                        entity.getPreparationMinutes(), entity.getCleanupMinutes(), entity.getPrice(),
                        entity.getCustomerCapacity()),
                entity.getStatus(), locationIds, requirements);
    }

    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
        if (constraint != null && constraint.endsWith(NAME_CONSTRAINT)) {
            return new DuplicateNameException(ex);
        }
        return ex;
    }
}
