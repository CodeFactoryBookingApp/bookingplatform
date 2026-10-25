package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceBookings;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-009 use cases. Each one checks access before anything else (OrganizationAccess is mocked:
 * its own rules are in OrganizationAccessTest) and translates the repository duplicates to 409.
 */
class ResourceUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID TYPE = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();

    private ResourceRepository repository;
    private OrganizationAccess access;

    @BeforeEach
    void setUp() {
        repository = mock(ResourceRepository.class);
        access = mock(OrganizationAccess.class);
    }

    private Resource stored(ResourceStatus status) {
        Resource resource = new Resource(UUID.randomUUID(), ORG, TYPE, LOCATION, "Silla 1", 1, status, List.of());
        when(repository.findById(ORG, resource.getId())).thenReturn(Optional.of(resource));
        return resource;
    }

    @Nested
    @DisplayName("Creating a resource type")
    class CreateType {

        private CreateResourceTypeUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new CreateResourceTypeUseCase(repository, access);
        }

        @Test
        @DisplayName("Only the owner or a manager may create one; the trimmed name is stored and a blank description is none")
        void createsTheType() {
            // Act
            ResourceType type = useCase.create(ORG, USER, "  Silla de barbero ", "  ");

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            assertEquals("Silla de barbero", type.name());
            assertNull(type.description());
            verify(repository).saveType(type);
        }

        @Test
        @DisplayName("HU-009: a type name already used by the provider is a 409 DUPLICATE_NAME")
        void duplicatedNameIsAConflict() {
            // Arrange
            doThrow(new DuplicateNameException(new IllegalStateException())).when(repository).saveType(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.create(ORG, USER, "Silla de barbero", null));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_NAME, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Registering a resource")
    class Register {

        private RegisterResourceUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new RegisterResourceUseCase(repository, access);
            when(repository.findType(ORG, TYPE)).thenReturn(Optional.of(new ResourceType(TYPE, ORG, "Silla", null)));
            when(access.ownsLocation(ORG, LOCATION)).thenReturn(true);
        }

        @Test
        @DisplayName("HU-009: a resource of one of the provider's types, in one of its locations, is stored ACTIVE")
        void registersTheResource() {
            // Act
            Resource resource = useCase.register(ORG, USER, TYPE, LOCATION, " Silla 1 ", 1);

            // Assert
            verify(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);
            ArgumentCaptor<Resource> saved = ArgumentCaptor.forClass(Resource.class);
            verify(repository).saveNew(saved.capture());
            assertEquals("Silla 1", saved.getValue().getName());
            assertEquals(ORG, saved.getValue().getOrganizationId());
            assertEquals(ResourceStatus.ACTIVE, resource.getStatus());
        }

        @Test
        @DisplayName("A type and a location that are not the provider's are both reported, and nothing is stored")
        void foreignTypeAndLocationAreRejected() {
            // Arrange
            UUID foreignType = UUID.randomUUID();
            UUID foreignLocation = UUID.randomUUID();
            when(repository.findType(ORG, foreignType)).thenReturn(Optional.empty());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.register(ORG, USER, foreignType, foreignLocation, "Silla", 1));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(2, ex.details().size());
            verify(repository, never()).saveNew(any());
        }

        @Test
        @DisplayName("A resource name already used in the location is a 409 DUPLICATE_NAME")
        void duplicatedNameIsAConflict() {
            // Arrange
            doThrow(new DuplicateNameException(new IllegalStateException())).when(repository).saveNew(any());

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.register(ORG, USER, TYPE, LOCATION, "Silla 1", 1));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_NAME, ex.errorCode());
        }

        @Test
        @DisplayName("A role that may not change resources stops the registration before anything is read")
        void accessIsCheckedFirst() {
            // Arrange
            doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                    .when(access).requireRole(ORG, USER, MemberRole.OWNER, MemberRole.MANAGER);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.register(ORG, USER, TYPE, LOCATION, "Silla 1", 1));

            // Assert
            assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
            verify(repository, never()).findType(any(), any());
        }
    }

    @Nested
    @DisplayName("Maintenance and retirement")
    class MaintenanceAndRetirement {

        @Test
        @DisplayName("HU-009: a maintenance period is stored for the resource")
        void schedulesTheMaintenance() {
            // Arrange
            Resource resource = stored(ResourceStatus.ACTIVE);
            ScheduleMaintenanceUseCase useCase = new ScheduleMaintenanceUseCase(repository, access);

            // Act
            useCase.schedule(ORG, USER, resource.getId(), NOW, NOW.plusSeconds(3600), " Cambio de silla ");

            // Assert
            ArgumentCaptor<MaintenanceWindow> window = ArgumentCaptor.forClass(MaintenanceWindow.class);
            verify(repository).addMaintenance(eq(resource.getId()), window.capture());
            assertEquals("Cambio de silla", window.getValue().reason());
        }

        @Test
        @DisplayName("HU-022: a resource of another provider is a 404, and nothing is stored")
        void foreignResourceIsNotFound() {
            // Arrange
            UUID foreign = UUID.randomUUID();
            when(repository.findById(ORG, foreign)).thenReturn(Optional.empty());
            ScheduleMaintenanceUseCase useCase = new ScheduleMaintenanceUseCase(repository, access);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.schedule(ORG, USER, foreign, NOW, NOW.plusSeconds(60), null));

            // Assert
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
            verify(repository, never()).addMaintenance(any(), any());
        }

        @Test
        @DisplayName("HU-009: the future bookings are counted from now, and a resource with some is not retired")
        void retirementCountsFutureBookingsFromNow() {
            // Arrange
            Resource resource = stored(ResourceStatus.ACTIVE);
            ResourceBookings bookings = mock(ResourceBookings.class);
            when(bookings.countFuture(resource.getId(), NOW)).thenReturn(1L);
            RetireResourceUseCase useCase = new RetireResourceUseCase(repository, bookings, access,
                    Clock.fixed(NOW, ZoneOffset.UTC));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.retire(ORG, USER, resource.getId()));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            verify(repository, never()).saveStatus(any());
        }

        @Test
        @DisplayName("A resource without future bookings is retired and its status stored")
        void retiresTheResource() {
            // Arrange
            Resource resource = stored(ResourceStatus.ACTIVE);
            RetireResourceUseCase useCase = new RetireResourceUseCase(repository, mock(ResourceBookings.class), access,
                    Clock.fixed(NOW, ZoneOffset.UTC));

            // Act
            Resource retired = useCase.retire(ORG, USER, resource.getId());

            // Assert
            assertEquals(ResourceStatus.RETIRED, retired.getStatus());
            verify(repository).saveStatus(resource);
        }
    }

    @Test
    @DisplayName("Listing requires membership only: any role of the provider may read")
    void listingRequiresMembership() {
        // Arrange
        ListResourcesUseCase useCase = new ListResourcesUseCase(repository, access);

        // Act
        useCase.resources(ORG, USER);

        // Assert
        verify(access).requireMember(ORG, USER);
        verify(repository).findAll(ORG);
    }
}
