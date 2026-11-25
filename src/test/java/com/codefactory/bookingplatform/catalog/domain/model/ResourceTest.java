package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-009 resource rules: capacity, maintenance periods, retirement and availability. Boundary
 * values on the half-open periods [startsAt, endsAt), state transitions over ResourceStatus.
 */
class ResourceTest {

    private static final Instant T0 = Instant.parse("2026-11-03T13:00:00Z");

    private static Instant at(int hours) {
        return T0.plus(Duration.ofHours(hours));
    }

    private static MaintenanceWindow window(int fromHours, int toHours) {
        return new MaintenanceWindow(UUID.randomUUID(), at(fromHours), at(toHours), "Revisión");
    }

    private static Resource resource() {
        return Resource.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Silla 1", 1);
    }

    private static Resource retired() {
        return new Resource(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Silla 9", 1,
                ResourceStatus.RETIRED, List.of());
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("HU-009: a new resource is ACTIVE, without maintenance, and available")
        void newResourceIsAvailable() {
            // Act
            Resource resource = resource();

            // Assert
            assertEquals(ResourceStatus.ACTIVE, resource.getStatus());
            assertEquals(ResourceCondition.AVAILABLE, resource.conditionAt(T0));
            assertTrue(resource.getMaintenances().isEmpty());
        }

        @ParameterizedTest(name = "[{index}] capacity {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("A capacity below one is rejected and names the field (boundary 0)")
        void capacityBelowOneIsRejected(int capacity) {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> Resource.create(UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Silla", capacity));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertTrue(ex.details().containsKey("capacity"));
        }
    }

    @Nested
    @DisplayName("Maintenance")
    class Maintenance {

        @Test
        @DisplayName("A period that does not end after it starts is rejected (boundary: equal instants)")
        void emptyPeriodIsRejected() {
            // Act
            Executable act = () -> window(2, 2);

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class, act).errorCode());
        }

        @Test
        @DisplayName("HU-009: while a maintenance lasts the resource is IN_MAINTENANCE and not available")
        void resourceInMaintenanceIsNotAvailable() {
            // Arrange
            Resource resource = resource();

            // Act
            resource.scheduleMaintenance(window(1, 48));

            // Assert
            assertEquals(ResourceCondition.IN_MAINTENANCE, resource.conditionAt(at(1)), "the start is included");
            assertFalse(resource.isAvailableDuring(at(10), at(11)));
            assertEquals(ResourceCondition.AVAILABLE, resource.conditionAt(at(0)), "before the start it serves");
            assertTrue(resource.isAvailableDuring(at(0), at(1)), "a slot that ends when the period starts is available");
        }

        @Test
        @DisplayName("The period is half-open: at its end the resource is available again (boundary)")
        void resourceIsAvailableAtTheEndOfTheMaintenance() {
            // Arrange
            Resource resource = resource();

            // Act
            resource.scheduleMaintenance(window(0, 2));

            // Assert
            assertEquals(ResourceCondition.AVAILABLE, resource.conditionAt(at(2)));
            assertTrue(resource.isAvailableDuring(at(2), at(3)));
            assertFalse(resource.isAvailableDuring(at(1), at(3)), "a slot that starts inside the period is not available");
        }

        @Test
        @DisplayName("Two overlapping periods of the same resource are a 409")
        void overlappingPeriodsAreRejected() {
            // Arrange
            Resource resource = resource();
            resource.scheduleMaintenance(window(0, 4));

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> resource.scheduleMaintenance(window(3, 6)));

            // Assert
            assertEquals(ErrorCode.OVERLAPPING_PERIOD, ex.errorCode());
            assertEquals(1, resource.getMaintenances().size());
        }

        @Test
        @DisplayName("A period that starts exactly when the previous one ends does not overlap it")
        void adjacentPeriodsAreAccepted() {
            // Arrange
            Resource resource = resource();
            resource.scheduleMaintenance(window(0, 4));

            // Act
            Executable act = () -> resource.scheduleMaintenance(window(4, 6));

            // Assert
            assertDoesNotThrow(act);
        }

        /** Equivalence partitions of a new period against an existing one [4h, 8h). */
        @ParameterizedTest(name = "[{index}] [{0}h, {1}h) against [4h, 8h)")
        @CsvSource({
                "4, 8",   // the same period
                "2, 10",  // contains the existing one
                "5, 7",   // inside the existing one
                "2, 5",   // overlaps its start
                "7, 9"    // overlaps its end (boundary: one hour before it ends)
        })
        @DisplayName("HU-009: any period that shares time with an existing one is a 409, and is not added")
        void everyKindOfOverlapIsRejected(int from, int to) {
            // Arrange
            Resource resource = resource();
            resource.scheduleMaintenance(window(4, 8));

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> resource.scheduleMaintenance(window(from, to)));

            // Assert
            assertEquals(ErrorCode.OVERLAPPING_PERIOD, ex.errorCode());
            assertEquals(1, resource.getMaintenances().size());
        }

        @Test
        @DisplayName("A period that ends exactly when an existing one starts does not overlap it")
        void periodEndingAtTheNextStartIsAccepted() {
            // Arrange
            Resource resource = resource();
            resource.scheduleMaintenance(window(4, 8));

            // Act
            MaintenanceWindow earlier = resource.scheduleMaintenance(window(1, 4));

            // Assert
            assertEquals(2, resource.getMaintenances().size());
            assertTrue(resource.getMaintenances().contains(earlier));
        }

        @ParameterizedTest(name = "[{index}] startsAt={0} endsAt={1}")
        @CsvSource(nullValues = "NULL", value = {
                "2026-11-03T15:00:00Z, 2026-11-03T14:59:59Z",
                "2026-11-03T15:00:00Z, NULL"
        })
        @DisplayName("A period that ends before it starts, or lacks an end, is a 400 on endsAt")
        void periodEndingBeforeItStartsIsRejected(Instant startsAt, Instant endsAt) {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> new MaintenanceWindow(UUID.randomUUID(), startsAt, endsAt, null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals("must be after startsAt", ex.details().get("endsAt"));
        }

        @Test
        @DisplayName("A retired resource reports RETIRED even inside a maintenance period")
        void retirementPrevailsOverMaintenance() {
            // Arrange
            Resource resource = new Resource(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), "Silla 9", 1, ResourceStatus.RETIRED, List.of(window(0, 4)));

            // Act
            ResourceCondition condition = resource.conditionAt(at(1));

            // Assert
            assertEquals(ResourceCondition.RETIRED, condition);
        }

        @Test
        @DisplayName("A retired resource cannot be scheduled for maintenance")
        void retiredResourceCannotBeScheduled() {
            // Arrange
            Resource resource = retired();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> resource.scheduleMaintenance(window(0, 2)));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Retirement")
    class Retirement {

        @Test
        @DisplayName("HU-009: a resource without future bookings is retired, and is no longer available")
        void resourceWithoutBookingsIsRetired() {
            // Arrange
            Resource resource = resource();

            // Act
            resource.retire(0);

            // Assert
            assertEquals(ResourceStatus.RETIRED, resource.getStatus());
            assertEquals(ResourceCondition.RETIRED, resource.conditionAt(T0));
            assertFalse(resource.isAvailableDuring(at(0), at(1)));
        }

        @Test
        @DisplayName("HU-009: a resource with future bookings cannot be retired; the error says how many")
        void resourceWithFutureBookingsIsNotRetired() {
            // Arrange
            Resource resource = resource();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> resource.retire(2));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            assertEquals("2", ex.details().get("futureBookings"));
            assertEquals(ResourceStatus.ACTIVE, resource.getStatus());
        }

        @Test
        @DisplayName("Retiring a retired resource is a 409")
        void retiringTwiceIsRejected() {
            // Arrange
            Resource resource = retired();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> resource.retire(0));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }
    }
}
