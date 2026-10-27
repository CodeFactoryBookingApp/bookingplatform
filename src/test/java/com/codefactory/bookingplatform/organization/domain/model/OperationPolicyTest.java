package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-002 operating rules. Boundary values around "minimum notice not greater than maximum" and
 * around zero, which is a valid value for every field.
 */
class OperationPolicyTest {

    @Test
    @DisplayName("HU-002: a minimum notice equal to the maximum is accepted (boundary)")
    void equalMinimumAndMaximumAreAccepted() {
        // Act
        Executable act = () -> new OperationPolicy(60, 60, 0);

        // Assert
        assertDoesNotThrow(act);
    }

    @Test
    @DisplayName("HU-002: a minimum notice greater than the maximum is rejected and names the field")
    void minimumAboveMaximumIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> new OperationPolicy(61, 60, 0));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals("must not be greater than bookingNoticeMinutesMax", ex.details().get("bookingNoticeMinutesMin"));
    }

    @ParameterizedTest(name = "[{index}] ({0}, {1}, {2}) -> {3}")
    @CsvSource({
            "-1, 60, 0, bookingNoticeMinutesMin",
            "0, -1, 0, bookingNoticeMinutesMax",
            "0, 60, -1, freeCancellationWindowMinutes"
    })
    @DisplayName("A negative value is rejected for each field (boundary just below zero)")
    void negativeValuesAreRejected(int min, int max, int window, String field) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> new OperationPolicy(min, max, window));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertTrue(ex.details().containsKey(field), ex.details()::toString);
    }

    static Stream<Arguments> combinedErrors() {
        String negative = "must not be negative";
        String aboveMax = "must not be greater than bookingNoticeMinutesMax";
        return Stream.of(
                Arguments.of(-1, -1, 0, Map.of("bookingNoticeMinutesMin", negative, "bookingNoticeMinutesMax", negative)),
                Arguments.of(-5, -1, 0, Map.of("bookingNoticeMinutesMin", negative, "bookingNoticeMinutesMax", negative)),
                Arguments.of(0, -1, 0, Map.of("bookingNoticeMinutesMin", aboveMax, "bookingNoticeMinutesMax", negative)),
                Arguments.of(61, 60, -1, Map.of("bookingNoticeMinutesMin", aboveMax, "freeCancellationWindowMinutes", negative)),
                Arguments.of(-1, 60, -1, Map.of("bookingNoticeMinutesMin", negative, "freeCancellationWindowMinutes", negative)),
                Arguments.of(-1, -1, -1, Map.of("bookingNoticeMinutesMin", negative, "bookingNoticeMinutesMax", negative,
                        "freeCancellationWindowMinutes", negative)));
    }

    /**
     * Decision table over the four conditions (min &lt; 0, max &lt; 0, window &lt; 0, min &gt; max): when
     * several hold at once, every field at fault is reported in the same 400, not only the first.
     */
    @ParameterizedTest(name = "[{index}] ({0}, {1}, {2}) -> {3}")
    @MethodSource("combinedErrors")
    @DisplayName("HU-002: several wrong values at once are all reported, each on its own field")
    void combinedErrorsAreAllReported(int min, int max, int window, Map<String, String> expected) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> new OperationPolicy(min, max, window));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(expected, ex.details());
    }

    @Test
    @DisplayName("Zero is a valid value for every field: book right now, cancel free until the start")
    void zeroIsValidEverywhere() {
        // Act
        Executable act = () -> new OperationPolicy(0, 0, 0);

        // Assert
        assertDoesNotThrow(act);
    }
}
