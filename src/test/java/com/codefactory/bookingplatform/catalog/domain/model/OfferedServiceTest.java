package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-004 service rules and the HU-009 activation rule: equivalence partitions and boundaries of
 * every numeric field, the activation decision table (location x requirement) and the status
 * transitions.
 */
class OfferedServiceTest {

    private static final UUID LOCATION_A = UUID.randomUUID();
    private static final UUID LOCATION_B = UUID.randomUUID();
    private static final UUID CHAIR = UUID.randomUUID();
    private static final UUID ROOM = UUID.randomUUID();

    private static OfferedService.Details details(int duration, int capacity, String price) {
        return new OfferedService.Details("Corte clásico", null, duration, 5, 5, new BigDecimal(price), capacity);
    }

    private static OfferedService service(List<UUID> locations, List<ResourceRequirement> requirements) {
        return OfferedService.create(UUID.randomUUID(), UUID.randomUUID(), details(30, 1, "35000"), locations,
                requirements);
    }

    private static OfferedService chairService() {
        return service(List.of(LOCATION_A), List.of(new ResourceRequirement(CHAIR, 1)));
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("HU-004: a service with a location and a required resource type is created INACTIVE, ready to be activated")
        void newServiceIsInactive() {
            // Act
            OfferedService service = chairService();

            // Assert
            assertEquals(ServiceStatus.INACTIVE, service.getStatus());
            assertFalse(service.isGroup());
        }

        @Test
        @DisplayName("HU-004: a group service with more than one place is accepted (boundary: 2)")
        void groupServiceIsAccepted() {
            // Act
            OfferedService service = OfferedService.create(UUID.randomUUID(), UUID.randomUUID(),
                    details(120, 2, "180000"), List.of(LOCATION_A), List.of(new ResourceRequirement(ROOM, 1)));

            // Assert
            assertTrue(service.isGroup());
        }

        @ParameterizedTest(name = "[{index}] duration={0} capacity={1} price={2} -> {3}")
        @CsvSource({
                "0, 1, 1000, durationMinutes",
                "30, 0, 1000, customerCapacity",
                "30, 1, -1, price",
                "30, 1, 10.005, price"
        })
        @DisplayName("Values out of range are rejected and name their field")
        void outOfRangeValuesAreRejected(int duration, int capacity, String price, String field) {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> OfferedService.create(UUID.randomUUID(),
                    UUID.randomUUID(), details(duration, capacity, price), List.of(LOCATION_A),
                    List.of(new ResourceRequirement(CHAIR, 1))));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertTrue(ex.details().containsKey(field), ex.details()::toString);
        }

        @Test
        @DisplayName("HU-004: a service without a location or without a required resource type is rejected")
        void locationsAndRequirementsAreRequired() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> service(List.of(), List.of()));

            // Assert
            assertEquals(Map.of("locationIds", "at least one location is required",
                    "requirements", "at least one required resource type is needed"), ex.details());
        }

        @Test
        @DisplayName("The same location or resource type listed twice is rejected")
        void duplicatesAreRejected() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> service(List.of(LOCATION_A, LOCATION_A),
                    List.of(new ResourceRequirement(CHAIR, 1), new ResourceRequirement(CHAIR, 2))));

            // Assert
            assertEquals(Map.of("locationIds", "a location is listed twice",
                    "requirements", "a resource type is listed twice"), ex.details());
        }

        @Test
        @DisplayName("Missing lists of locations and requirements are rejected as missing, not as a failure")
        void nullListsAreRejectedAsMissing() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> service(null, null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("locationIds", "at least one location is required",
                    "requirements", "at least one required resource type is needed"), ex.details());
        }

        @Test
        @DisplayName("A requirement needs at least one resource")
        void requirementQuantityIsAtLeastOne() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> new ResourceRequirement(CHAIR, 0));

            // Assert
            assertTrue(ex.details().containsKey("quantity"));
        }
    }

    /**
     * HU-004 limits of every numeric field, by boundary values (n-1 rejected, n accepted): duration
     * at least 1, preparation and cleanup not negative, price zero or more with at most two
     * decimals, capacity at least 1 (1 individual, 2 the smallest group).
     */
    @Nested
    @DisplayName("Boundaries of the service details (HU-004)")
    class DetailBoundaries {

        private static OfferedService.Details details(int duration, int preparation, int cleanup, String price,
                                                      int capacity) {
            return new OfferedService.Details("Corte clásico", null, duration, preparation, cleanup,
                    price == null ? null : new BigDecimal(price), capacity);
        }

        private static OfferedService create(OfferedService.Details details) {
            return OfferedService.create(UUID.randomUUID(), UUID.randomUUID(), details, List.of(LOCATION_A),
                    List.of(new ResourceRequirement(CHAIR, 1)));
        }

        @Test
        @DisplayName("The smallest valid values are accepted: 1 minute, no preparation or cleanup, free, individual")
        void smallestValidValuesAreAccepted() {
            // Act
            OfferedService service = create(details(1, 0, 0, "0", 1));

            // Assert
            assertEquals(details(1, 0, 0, "0", 1), service.details());
            assertFalse(service.isGroup(), "a capacity of 1 is an individual service");
        }

        @Test
        @DisplayName("A price with exactly two decimals is accepted (boundary: scale 2)")
        void priceWithTwoDecimalsIsAccepted() {
            // Act
            OfferedService service = create(details(30, 0, 0, "0.01", 1));

            // Assert
            assertEquals(new BigDecimal("0.01"), service.getPrice());
        }

        @ParameterizedTest(name = "[{index}] duration={0} preparation={1} cleanup={2} price={3} capacity={4} -> {5}")
        @CsvSource(nullValues = "NULL", value = {
                "0, 0, 0, 0, 1, durationMinutes, must be at least 1",
                "1, -1, 0, 0, 1, preparationMinutes, must not be negative",
                "1, 0, -1, 0, 1, cleanupMinutes, must not be negative",
                "1, 0, 0, -0.01, 1, price, 'must be zero or more, with at most two decimals'",
                "1, 0, 0, NULL, 1, price, 'must be zero or more, with at most two decimals'",
                "1, 0, 0, 0, 0, customerCapacity, 'must be at least 1: 1 is individual, more is a group'"
        })
        @DisplayName("One step below each limit is rejected, and only that field is reported")
        void oneBelowEachLimitIsRejected(int duration, int preparation, int cleanup, String price, int capacity,
                                         String field, String message) {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> create(details(duration, preparation, cleanup, price, capacity)));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of(field, message), ex.details());
        }

        @Test
        @DisplayName("Every invalid field is reported at once, so the provider fixes them in one go")
        void everyInvalidFieldIsReported() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> create(details(0, -1, -1, "-1", 0)));

            // Assert
            assertEquals(Set.of("durationMinutes", "preparationMinutes", "cleanupMinutes", "price",
                    "customerCapacity"), ex.details().keySet());
        }
    }

    @Nested
    @DisplayName("Activation (HU-009)")
    class Activation {

        @Test
        @DisplayName("HU-009: with enough active resources in every location the service becomes ACTIVE")
        void enoughResourcesActivates() {
            // Arrange
            OfferedService service = chairService();

            // Act
            service.activate((location, type) -> 1L);

            // Assert
            assertEquals(ServiceStatus.ACTIVE, service.getStatus());
        }

        @Test
        @DisplayName("HU-009: without any active instance of the required type the service is not activated")
        void noInstanceIsAConflict() {
            // Arrange
            OfferedService service = chairService();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> service.activate((location, type) -> 0L));

            // Assert
            assertEquals(ErrorCode.INSUFFICIENT_RESOURCES, ex.errorCode());
            assertEquals(Map.of(LOCATION_A + "/" + CHAIR, "requires 1, has 0"), ex.details());
            assertEquals(ServiceStatus.INACTIVE, service.getStatus());
        }

        @Test
        @DisplayName("Every location must cover the quantity: one short location is enough to refuse (boundary: one less)")
        void oneShortLocationRefusesActivation() {
            // Arrange
            OfferedService service = service(List.of(LOCATION_A, LOCATION_B), List.of(new ResourceRequirement(CHAIR, 4)));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.activate((location, type) -> location.equals(LOCATION_A) ? 4L : 3L));

            // Assert
            assertEquals(Map.of(LOCATION_B + "/" + CHAIR, "requires 4, has 3"), ex.details());
        }

        @Test
        @DisplayName("Decision table location x type: every uncovered pair is reported, the covered ones are not")
        void everyShortfallIsReported() {
            // Arrange
            OfferedService service = service(List.of(LOCATION_A, LOCATION_B),
                    List.of(new ResourceRequirement(CHAIR, 2), new ResourceRequirement(ROOM, 1)));
            Map<String, Long> active = Map.of(
                    LOCATION_A + "/" + CHAIR, 2L, LOCATION_A + "/" + ROOM, 0L,
                    LOCATION_B + "/" + CHAIR, 1L, LOCATION_B + "/" + ROOM, 1L);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.activate((location, type) -> active.get(location + "/" + type)));

            // Assert
            assertEquals(Map.of(LOCATION_A + "/" + ROOM, "requires 1, has 0",
                    LOCATION_B + "/" + CHAIR, "requires 2, has 1"), ex.details());
            assertEquals(ServiceStatus.INACTIVE, service.getStatus());
        }

        @Test
        @DisplayName("Activating an active service is a 409")
        void activatingTwiceIsRejected() {
            // Arrange
            OfferedService service = chairService();
            service.activate((location, type) -> 1L);

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> service.activate((location, type) -> 1L));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Update and deactivation")
    class UpdateAndDeactivation {

        @Test
        @DisplayName("HU-004: an update changes the details, and keeps locations, requirements and status")
        void updateChangesTheDetails() {
            // Arrange
            OfferedService service = chairService();

            // Act
            service.update(new OfferedService.Details("Corte premium", "Con lavado", 45, 10, 10,
                    new BigDecimal("50000.50"), 1));

            // Assert
            assertEquals(new OfferedService.Details("Corte premium", "Con lavado", 45, 10, 10,
                    new BigDecimal("50000.50"), 1), service.details());
            assertEquals(List.of(LOCATION_A), service.getLocationIds());
            assertEquals(ServiceStatus.INACTIVE, service.getStatus());
        }

        @Test
        @DisplayName("An update with invalid values is rejected and changes nothing")
        void invalidUpdateChangesNothing() {
            // Arrange
            OfferedService service = chairService();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.update(details(0, 1, "35000")));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(30, service.getDurationMinutes());
        }

        @Test
        @DisplayName("HU-004: an active service is deactivated; deactivating it again is a 409")
        void deactivation() {
            // Arrange
            OfferedService service = chairService();
            service.activate((location, type) -> 1L);
            service.deactivate();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, service::deactivate);

            // Assert
            assertEquals(ServiceStatus.INACTIVE, service.getStatus());
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }
    }
}
