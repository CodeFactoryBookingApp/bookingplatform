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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
            OfferedService created = useCase.create(ORG, USER, DETAILS, List.of(LOCATION),
                    List.of(new ResourceRequirement(CHAIR, 1)));

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            ArgumentCaptor<OfferedService> saved = ArgumentCaptor.forClass(OfferedService.class);
            verify(services).saveNew(saved.capture());
            assertEquals(new OfferedService.Details("Corte clásico", null, 30, 5, 5, new BigDecimal("35000"), 1),
                    saved.getValue().details());
            assertNull(saved.getValue().getDescription());
            assertEquals(ServiceStatus.INACTIVE, saved.getValue().getStatus());
            assertEquals(ORG, saved.getValue().getOrganizationId(), "the service belongs to the provider it was created in");
            assertSame(saved.getValue(), created);
        }

        /** Equivalence partitions of the description: absent, blank, padded text. */
        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource(nullValues = "NULL", value = {"NULL, NULL", "'  Con lavado y secado  ', Con lavado y secado"})
        @DisplayName("HU-004: a description is stored trimmed; an absent one stays absent")
        void descriptionIsTrimmed(String description, String stored) {
            // Arrange
            OfferedService.Details details = new OfferedService.Details("Corte", description, 30, 0, 0,
                    new BigDecimal("35000"), 1);

            // Act
            OfferedService created = useCase.create(ORG, USER, details, List.of(LOCATION),
                    List.of(new ResourceRequirement(CHAIR, 1)));

            // Assert
            assertEquals(stored, created.getDescription());
        }

        @Test
        @DisplayName("A foreign location and resource type in the first position are reported too (index 0)")
        void foreignReferencesInFirstPositionAreRejected() {
            // Arrange
            UUID foreignLocation = UUID.randomUUID();
            UUID foreignType = UUID.randomUUID();
            when(resources.findType(ORG, foreignType)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.create(ORG, USER, DETAILS,
                    List.of(foreignLocation, LOCATION),
                    List.of(new ResourceRequirement(foreignType, 1), new ResourceRequirement(CHAIR, 1))));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("locationIds[0]", "not a location of this provider",
                    "requirements[0].resourceTypeId", "not a resource type of this provider"), ex.details());
            verify(services, never()).saveNew(any());
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
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
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
        @DisplayName("HU-004: an update stores the new details trimmed, and keeps locations, requirements and status")
        void updateStoresTheNewDetails() {
            // Arrange
            OfferedService service = stored();
            OfferedService.Details changes = new OfferedService.Details("  Corte premium ", "  Con lavado ", 45, 10,
                    10, new BigDecimal("50000.50"), 1);

            // Act
            OfferedService updated = useCase.update(ORG, USER, service.getId(), changes);

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            assertEquals(new OfferedService.Details("Corte premium", "Con lavado", 45, 10, 10,
                    new BigDecimal("50000.50"), 1), updated.details());
            assertEquals(List.of(LOCATION), updated.getLocationIds());
            assertEquals(List.of(new ResourceRequirement(CHAIR, 1)), updated.getRequirements());
            assertEquals(ServiceStatus.INACTIVE, updated.getStatus());
            verify(services).save(service);
            assertSame(service, updated);
        }

        @Test
        @DisplayName("An update with invalid values is a 400, and nothing is stored")
        void invalidUpdateStoresNothing() {
            // Arrange
            OfferedService service = stored();
            OfferedService.Details invalid = new OfferedService.Details("Corte", null, 0, 5, 5,
                    new BigDecimal("35000"), 1);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.update(ORG, USER, service.getId(), invalid));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verify(services, never()).save(any());
        }

        @Test
        @DisplayName("A role that may not change services is stopped before the service is read")
        void accessIsCheckedFirst() {
            // Arrange
            OfferedService service = stored();
            doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                    .when(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.update(ORG, USER, service.getId(), DETAILS));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(services, never()).findById(any(), any());
            verify(services, never()).save(any());
        }

        @Test
        @DisplayName("HU-009: each location is checked for each required type before activating")
        void activationChecksEveryLocationAndType() {
            // Arrange
            UUID otherLocation = UUID.randomUUID();
            UUID room = UUID.randomUUID();
            OfferedService service = new OfferedService(UUID.randomUUID(), ORG,
                    new OfferedService.Details("Corte", null, 30, 0, 0, BigDecimal.TEN, 1), ServiceStatus.INACTIVE,
                    List.of(LOCATION, otherLocation),
                    List.of(new ResourceRequirement(CHAIR, 1), new ResourceRequirement(room, 1)));
            when(services.findById(ORG, service.getId())).thenReturn(Optional.of(service));
            when(resources.countActive(any(), any())).thenReturn(1L);
            when(resources.countActive(otherLocation, room)).thenReturn(0L);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.activate(ORG, USER, service.getId()));

            // Assert
            assertEquals(Map.of(otherLocation + "/" + room, "requires 1, has 0"), ex.details());
            verify(resources).countActive(LOCATION, CHAIR);
            verify(resources).countActive(LOCATION, room);
            verify(resources).countActive(otherLocation, CHAIR);
            verify(services, never()).save(any());
        }

        @Test
        @DisplayName("Activating a service that is already active is a 409, and nothing is stored")
        void activatingAnActiveServiceIsRejected() {
            // Arrange
            OfferedService service = stored();
            when(resources.countActive(LOCATION, CHAIR)).thenReturn(1L);
            service.activate(resources::countActive);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.activate(ORG, USER, service.getId()));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(services, never()).save(any());
        }

        @Test
        @DisplayName("HU-004: an active service is deactivated without being deleted, and its status is stored")
        void deactivatesTheService() {
            // Arrange
            OfferedService service = stored();
            service.activate((location, type) -> 1L);

            // Act
            OfferedService deactivated = useCase.deactivate(ORG, USER, service.getId());

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            assertEquals(ServiceStatus.INACTIVE, deactivated.getStatus());
            verify(services).save(service);
            verify(services, never()).delete(any());
            assertSame(service, deactivated);
        }

        @Test
        @DisplayName("Deactivating an inactive service is a 409, and nothing is stored")
        void deactivatingAnInactiveServiceIsRejected() {
            // Arrange
            OfferedService service = stored();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.deactivate(ORG, USER, service.getId()));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
            verify(services, never()).save(any());
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

    @Test
    @DisplayName("HU-022: someone outside the provider cannot list its services, and they are not read")
    void outsiderCannotListServices() {
        // Arrange
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)).when(access).requireMember(ORG, USER);
        ListServicesUseCase useCase = new ListServicesUseCase(services, access);

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.all(ORG, USER));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verify(services, never()).findAll(any());
    }
}
