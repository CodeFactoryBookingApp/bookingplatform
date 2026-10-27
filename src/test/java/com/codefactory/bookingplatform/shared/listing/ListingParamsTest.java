package com.codefactory.bookingplatform.shared.listing;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parameters every listing shares (HU-003, HU-005): a mistake is a 400 that names the parameter. */
class ListingParamsTest {

    private enum Column { NAME, REGISTERED }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"registered", " Registered "})
    @DisplayName("The sort column is read in any case and with surrounding blanks")
    void sortIgnoresCase(String value) {
        // Act
        Column column = ListingParams.sort(value, Column.class);

        // Assert
        assertEquals(Column.REGISTERED, column);
    }

    @Test
    @DisplayName("An unknown sort column is a 400 that lists the allowed ones")
    void unknownSortIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> ListingParams.sort("price", Column.class));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals("must be one of: name, registered", ex.details().get("sort"));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"asc, false", "DESC, true", "' desc ', true"})
    @DisplayName("The direction is asc or desc, in any case")
    void direction(String value, boolean descending) {
        // Act
        boolean result = ListingParams.descending(value);

        // Assert
        assertEquals(descending, result);
    }

    @Test
    @DisplayName("Any other direction is a 400")
    void unknownDirectionIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> ListingParams.descending("up"));

        // Assert
        assertTrue(ex.details().containsKey("direction"));
    }

    @Test
    @DisplayName("A negative page and a size outside 1..100 are both reported")
    void window() {
        // Arrange
        Map<String, String> errors = new LinkedHashMap<>();

        // Act
        ListingParams.checkWindow(-1, 101, errors);

        // Assert
        assertEquals(List.of("page", "size"), List.copyOf(errors.keySet()));
    }

    @Test
    @DisplayName("A range ending the day it starts is valid; one ending the day before is not")
    void range() {
        // Arrange
        LocalDate day = LocalDate.of(2026, 10, 25);
        Map<String, String> sameDay = new LinkedHashMap<>();
        Map<String, String> inverted = new LinkedHashMap<>();

        // Act
        ListingParams.checkRange(day, day, "to", sameDay);
        ListingParams.checkRange(day, day.minusDays(1), "to", inverted);

        // Assert
        assertTrue(sameDay.isEmpty());
        assertEquals(Map.of("to", "must not be before the start of the range"), inverted);
    }
}
