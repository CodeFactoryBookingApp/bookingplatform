package com.codefactory.bookingplatform.shared.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HU-003 export format: RFC 4180 quoting, CSV injection guard, BOM and CRLF line ends. */
class CsvTest {

    @Test
    @DisplayName("The file starts with a BOM and has one CRLF-terminated line per row, header first")
    void layout() {
        // Act
        String csv = Csv.of(List.of("id", "nombre"), List.of(List.of("1", "Ana"), List.of("2", "Luis")));

        // Assert
        assertEquals("﻿id,nombre\r\n1,Ana\r\n2,Luis\r\n", csv);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "Barbería, El Poblado|\"Barbería, El Poblado\"",
            "Dice \"hola\"|\"Dice \"\"hola\"\"\"",
            "Calle 1|Calle 1"
    })
    @DisplayName("Commas and quotes are quoted, quotes doubled; plain text is left alone")
    void quoting(String value, String expected) {
        // Act
        String field = Csv.field(value);

        // Assert
        assertEquals(expected, field);
    }

    @Test
    @DisplayName("A line break inside a field is quoted, so the row is not split")
    void lineBreakIsQuoted() {
        // Act
        String field = Csv.field("primera\nsegunda");

        // Assert
        assertEquals("\"primera\nsegunda\"", field);
    }

    static Stream<Arguments> formulas() {
        return Stream.of(
                Arguments.of("=HYPERLINK(\"http://x\")", "\"'=HYPERLINK(\"\"http://x\"\")\""),
                Arguments.of("+573001112233", "'+573001112233"),
                Arguments.of("-5", "'-5"),
                Arguments.of("@SUM(A1)", "'@SUM(A1)"),
                Arguments.of("\tx", "'\tx"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("formulas")
    @DisplayName("A field that starts like a formula is neutralised with an apostrophe (CSV injection)")
    void formulasAreNeutralised(String value, String expected) {
        // Act
        String field = Csv.field(value);

        // Assert
        assertEquals(expected, field);
    }

    @Test
    @DisplayName("A null or empty value is an empty field")
    void nullAndEmptyAreEmpty() {
        // Act
        String csv = Csv.of(List.of("a", "b", "c"), List.of(Arrays.asList(null, "", "x")));

        // Assert
        assertTrue(csv.endsWith("\r\n,,x\r\n"), csv);
    }
}
