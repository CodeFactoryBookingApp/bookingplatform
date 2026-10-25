package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import com.codefactory.bookingplatform.catalog.domain.model.ServiceStatus;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceBookings;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-004 use cases: access first, provider-owned references, duplicates as 409, and what is stored. */
class ServiceUseCasesTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID CHAIR = UUID.randomUUID();
    private static final OfferedService.Details DETAILS = new OfferedService.Details("  Corte clásico ", "  ", 30, 5, 5,
            new BigDecimal("35000"), 1);

    private ServiceRepository services;
    private ResourceRepository resources;
    private ServiceBookings bookings;
    private OrganizationAccess access;

    @BeforeEach
    void setUp() {
        services = mock(ServiceRepository.class);
        resources = mock(ResourceRepository.class);
        bookings = mock(ServiceBookings.class);
        access = mock(OrganizationAccess.class);
        when(access.ownsLocation(ORG, LOCATION)).thenReturn(true);
        when(resources.findType(ORG, CHAIR)).thenReturn(Optional.of(new ResourceType(CHAIR, ORG, "Silla", null)));
    }

    private OfferedService stored() {
        OfferedService service = new OfferedService(UUID.randomUUID(), ORG,
                new OfferedService.Details("Corte", null, 30, 5, 5, new BigDecimal("35000.00"), 1),
                ServiceStatus.INACTIVE, List.of(LOCATION), List.of(new ResourceRequirement(CHAIR, 1)));
        when(services.findById(ORG, service.getId())).thenReturn(Optional.of(service));
        return service;
    }

    @Nested
    @DisplayName("Creating a service")
    class Create {

        private CreateServiceUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new CreateServiceUseCase(services, resources, access);
        }

        @Test
        @DisplayName("HU-004: the owner or a manager creates it INACTIVE; the name is trimmed and a blank description is none")
        void createsTheService() {
            // Act
            useCase.create(ORG, USER, DETAILS, List.of(LOCATION), List.of(new ResourceRequirement(CHAIR, 1)));

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            ArgumentCaptor<OfferedService> saved = ArgumentCaptor.forClass(OfferedService.class);
            verify(services).saveNew(saved.capture());
            assertEquals("Corte clásico", saved.getValue().getName());
            assertNull(saved.getValue().getDescription());
            assertEquals(ServiceStatus.INACTIVE, saved.getValue().getStatus());
        }

        @Test
        @DisplayName("A location and a resource type that are not the provider's are reported by position")
        void foreignReferencesAreRejected() {
            // Arrange
            UUID foreignLocation = UUID.randomUUID();
            UUID foreignType = UUID.randomUUID();
            when(resources.findType(ORG, foreignType)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.create(ORG, USER, DETAILS,
                    List.of(LOCATION, foreignLocation),
                    List.of(new ResourceRequirement(CHAIR, 1), new ResourceRequirement(foreignType, 1))));

            // Assert
            assertEquals("not a location of this provider", ex.details().get("locationIds[1]"));
            assertEquals("not a resource type of this provider", ex.details().get("requirements[1].resourceTypeId"));
            verify(services, never()).saveNew(any());
        }

        @Test
        @DisplayName("HU-004: a service name already used by the provider is a 409 DUPLICATE_NAME")
        void duplicatedNameIsAConflict() {
            // Arrange
            doThrow(new DuplicateNameException(new IllegalStateException())).when(services).saveNew(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.create(ORG, USER, DETAILS,
                    List.of(LOCATION), List.of(new ResourceRequirement(CHAIR, 1))));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_NAME, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Managing a service")
    class Manage {

        private ManageServiceUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new ManageServiceUseCase(services, resources, bookings, access);
        }

        @Test
        @DisplayName("HU-009: activation counts the active resources of each type in each location")
        void activationCountsActiveResources() {
            // Arrange
            OfferedService service = stored();
            when(resources.countActive(LOCATION, CHAIR)).thenReturn(1L);

            // Act
            OfferedService activated = useCase.activate(ORG, USER, service.getId());

            // Assert
            assertEquals(ServiceStatus.ACTIVE, activated.getStatus());
            verify(services).save(service);
        }

        @Test
        @DisplayName("HU-009: without active resources the service is not activated and nothing is stored")
        void activationWithoutResourcesStoresNothing() {
            // Arrange
            OfferedService service = stored();
            when(resources.countActive(LOCATION, CHAIR)).thenReturn(0L);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.activate(ORG, USER, service.getId()));

            // Assert
            assertEquals(ErrorCode.INSUFFICIENT_RESOURCES, ex.errorCode());
            verify(services, never()).save(any());
        }

        @Test
        @DisplayName("HU-004: a service with bookings is not deleted: 409 HAS_BOOKINGS")
        void serviceWithBookingsIsNotDeleted() {
            // Arrange
            OfferedService service = stored();
            when(bookings.exist(service.getId())).thenReturn(true);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.delete(ORG, USER, service.getId()));

            // Assert
            assertEquals(ErrorCode.HAS_BOOKINGS, ex.errorCode());
            verify(services, never()).delete(any());
        }

        @Test
        @DisplayName("A service never booked is deleted")
        void serviceWithoutBookingsIsDeleted() {
            // Arrange
            OfferedService service = stored();

            // Act
            useCase.delete(ORG, USER, service.getId());

            // Assert
            verify(services).delete(service.getId());
        }

        @Test
        @DisplayName("A rename to a name in use is a 409 DUPLICATE_NAME")
        void renameToANameInUseIsAConflict() {
            // Arrange
            OfferedService service = stored();
            doThrow(new DuplicateNameException(new IllegalStateException())).when(services).save(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.update(ORG, USER, service.getId(), DETAILS));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_NAME, ex.errorCode());
        }

        @Test
        @DisplayName("HU-022: a service of another provider is a 404")
        void foreignServiceIsNotFound() {
            // Arrange
            UUID foreign = UUID.randomUUID();
            when(services.findById(ORG, foreign)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.deactivate(ORG, USER, foreign));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }

    @Test
    @DisplayName("Listing requires membership only: any role of the provider may read")
    void listingRequiresMembership() {
        // Arrange
        OfferedService service = stored();
        ListServicesUseCase useCase = new ListServicesUseCase(services, access);

        // Act
        OfferedService found = useCase.one(ORG, USER, service.getId());

        // Assert
        verify(access).requireMember(ORG, USER);
        assertEquals(service, found);
    }
}
