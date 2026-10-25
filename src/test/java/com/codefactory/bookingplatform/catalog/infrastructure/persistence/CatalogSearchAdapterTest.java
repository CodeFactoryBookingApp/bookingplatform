package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The keyword is a literal for LIKE: a client typing % or _ searches for them, not for "anything". */
class CatalogSearchAdapterTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "corte|corte",
            "100%|100\\%",
            "a_b|a\\_b",
            "c:\\x|c:\\\\x"
    })
    @DisplayName("LIKE wildcards and the escape character are escaped; ordinary text is kept")
    void wildcardsAreEscaped(String keyword, String expected) {
        // Act
        String literal = CatalogSearchAdapter.likeLiteral(keyword);

        // Assert
        assertEquals(expected, literal);
    }
}
