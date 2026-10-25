package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HU-005 search criteria: defaults, and boundary values of the price range and the page. */
class CatalogCriteriaTest {

    private static CatalogCriteria criteria(String keyword, String min, String max, int page, int size) {
        return new CatalogCriteria(keyword, null, null, null, min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), null, false, page, size);
    }

    @Test
    @DisplayName("Without a sort the catalog is sorted by name; a blank keyword means no keyword, others are trimmed")
    void defaults() {
        // Act
        CatalogCriteria blank = criteria("   ", null, null, 0, 20);

        // Assert
        assertNull(blank.keyword());
        assertEquals(CatalogCriteria.Sort.NAME, blank.sort());
        assertEquals("corte", criteria("  corte ", null, null, 0, 20).keyword());
    }

    @Test
    @DisplayName("HU-005: a price range whose minimum is above its maximum is rejected")
    void invertedPriceRangeIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> criteria(null, "5000", "4999", 0, 20));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertTrue(ex.details().containsKey("maxPrice"));
    }

    @Test
    @DisplayName("A range of a single price is valid (boundary: minimum equal to maximum)")
    void singlePriceRangeIsValid() {
        // Act
        Executable act = () -> criteria(null, "5000", "5000", 0, 20);

        // Assert
        assertDoesNotThrow(act);
    }

    @ParameterizedTest(name = "[{index}] page={0} size={1} -> {2}")
    @CsvSource({"-1, 20, page", "0, 0, size", "0, 101, size"})
    @DisplayName("A negative page, or a size outside 1..100, is rejected")
    void pageAndSizeOutOfRangeAreRejected(int page, int size, String field) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> criteria(null, null, null, page, size));

        // Assert
        assertTrue(ex.details().containsKey(field), ex.details()::toString);
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {1, 100})
    @DisplayName("Sizes 1 and 100 are the limits of the valid range")
    void sizeLimitsAreValid(int size) {
        // Act
        Executable act = () -> criteria(null, null, null, 0, size);

        // Assert
        assertDoesNotThrow(act);
    }

    @Test
    @DisplayName("A negative minimum price is rejected")
    void negativeMinimumIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> criteria(null, "-1", null, 0, 20));

        // Assert
        assertTrue(ex.details().containsKey("minPrice"));
    }
}
