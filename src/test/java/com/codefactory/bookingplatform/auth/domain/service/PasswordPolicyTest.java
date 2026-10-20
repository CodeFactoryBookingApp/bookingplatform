package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-021 - password security policy.
 *
 * <p>Black box: equivalence partitions over length (too short / valid / too long) and over the four
 * character classes; boundary value analysis at 7-8 and 72-73; decision table over the combinations
 * of missing classes. White box: every {@code if} in {@link PasswordPolicy#violations(String)},
 * both sides of the compound conditions {@code password == null || length < MIN} and
 * {@code password != null && length > MAX}, and both outcomes of each of the four regex probes.</p>
 *
 * <p>Every test follows Arrange-Act-Assert with a single Act per test.</p>
 */
class PasswordPolicyTest {

    private static final String TOO_SHORT = "Password must be at least 8 characters long";
    private static final String TOO_LONG = "Password must be at most 72 characters long";
    private static final String NO_UPPER = "Password must contain at least one uppercase letter";
    private static final String NO_LOWER = "Password must contain at least one lowercase letter";
    private static final String NO_DIGIT = "Password must contain at least one digit";
    private static final String NO_SPECIAL = "Password must contain at least one special character";

    /**
     * Builds a password of exactly {@code length} characters that satisfies every character-class
     * rule, so the only variable left under test is the length itself.
     */
    private static String passwordOfLength(int length) {
        String seed = "Aa1!";
        if (length <= seed.length()) {
            return seed.substring(0, length);
        }
        return seed + "x".repeat(length - seed.length());
    }

    // ----------------------------------------------------------------------------------------
    // Length: boundary value analysis around MIN_LENGTH (8) and MAX_LENGTH (72)
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Length rule")
    class LengthRule {

        @ParameterizedTest(name = "length {0} is rejected as too short")
        @ValueSource(ints = {1, 7})
        @DisplayName("A password shorter than 8 characters is rejected for length even if it has every character class")
        void belowMinimumLengthIsRejected(int length) {
            // Arrange
            String password = passwordOfLength(length);

            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertTrue(violations.contains(TOO_SHORT), () -> "Expected the too-short violation, got " + violations);
        }

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        @DisplayName("A password between 8 and 72 characters that meets every character class is accepted")
        void lengthsInsideTheRangeAreAccepted(int length) {
            // Arrange
            String password = passwordOfLength(length);

            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertIterableEquals(List.of(), violations,
                    () -> "Expected no violations for a valid password of length " + length);
        }

        @ParameterizedTest(name = "length {0} is rejected as too long")
        @ValueSource(ints = {100})
        @DisplayName("A password longer than 72 characters is rejected for length")
        void aboveMaximumLengthIsRejected(int length) {
            // Arrange
            String password = passwordOfLength(length);

            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertTrue(violations.contains(TOO_LONG), () -> "Expected the too-long violation, got " + violations);
        }

        @Test
        @DisplayName("An over-long password whose only defect is the length reports just that one violation")
        void tooLongReportsOnlyTheLengthViolation() {
            // Arrange
            String password = passwordOfLength(73);

            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertIterableEquals(List.of(TOO_LONG), violations);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Null and empty: the degenerate partitions
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Null and empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("A null password reports only the minimum-length violation, because no character rule can be evaluated")
        void nullReportsOnlyTheLengthViolation() {
            // Act
            List<String> violations = PasswordPolicy.violations(null);

            // Assert
            assertIterableEquals(List.of(TOO_SHORT), violations);
        }

        @Test
        @DisplayName("An empty password breaks the length rule and all four character-class rules at once")
        void emptyPasswordBreaksEveryRule() {
            // Act
            List<String> violations = PasswordPolicy.violations("");

            // Assert
            assertIterableEquals(List.of(TOO_SHORT, NO_UPPER, NO_LOWER, NO_DIGIT, NO_SPECIAL), violations);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Neither null nor an empty password is ever valid")
        void nullAndEmptyAreNeverValid(String password) {
            // Act
            boolean valid = PasswordPolicy.isValid(password);

            // Assert
            assertFalse(valid);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Character classes: one requirement broken at a time (decision table)
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Character-class rules")
    class CharacterClassRules {

        @ParameterizedTest(name = "\"{0}\" reports exactly [{1}]")
        @CsvSource({
                "str0ng!pass, Password must contain at least one uppercase letter",
                "STR0NG!PASS, Password must contain at least one lowercase letter",
                "Strong!Pass, Password must contain at least one digit",
                "Str0ngPassw, Password must contain at least one special character"
        })
        @DisplayName("Breaking a single character-class requirement reports that requirement and nothing else")
        void singleMissingClassReportsOnlyThatViolation(String password, String expectedViolation) {
            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertIterableEquals(List.of(expectedViolation), violations);
        }

        @Test
        @DisplayName("A password missing three character classes reports the three of them in rule order")
        void threeMissingClassesAreAllReported() {
            // Act
            List<String> violations = PasswordPolicy.violations("todolowercase");

            // Assert
            assertIterableEquals(List.of(NO_UPPER, NO_DIGIT, NO_SPECIAL), violations);
        }

        @ParameterizedTest(name = "\"{0}\" is accepted")
        @ValueSource(strings = {
                "Str0ng!Pass",
                "Aa1€€€€€",
                "Aa1¡¿test",
                "Aa1_underscore"
        })
        @DisplayName("Punctuation and symbols count as special, including non-ASCII symbols and the underscore")
        void symbolsCountAsSpecial(String password) {
            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertIterableEquals(List.of(), violations,
                    () -> "Expected \"" + password + "\" to be accepted");
        }
    }

    // ----------------------------------------------------------------------------------------
    // isValid: the verdict of the whole policy, written out rather than recomputed
    // ----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "isValid({0}) is {1}")
    @CsvSource({
            // La contraseña, y si la política debe aceptarla. El veredicto se escribe aquí,
            // no se calcula: comparar isValid contra violations().isEmpty() asevera X == X,
            // porque isValid está implementado exactamente así, y pasaría aunque la política
            // estuviese desactivada por completo.
            "'Segura#2026',     true",
            "'Abcdefg1!',       true",
            "'Sin1nguno',       false",   // sin carácter especial
            "'sinmayuscula1!',  false",   // sin mayúscula
            "'SINMINUSCULA1!',  false",   // sin minúscula
            "'SinDigitos!!',    false",   // sin dígito
            "'Ab1!',            false",   // por debajo del mínimo
            "'abcdefgh',        false"    // solo minúsculas
    })
    @DisplayName("The policy accepts a password only when it satisfies every rule at once")
    void isValidReflectsTheWholePolicy(String password, boolean esperado) {
        // Act
        boolean valid = PasswordPolicy.isValid(password);

        // Assert
        assertEquals(esperado, valid,
                () -> "the policy disagreed on [" + password + "]");
    }

    @Test
    @DisplayName("The returned violation list is a fresh list on every call, so callers cannot poison the policy")
    void violationsReturnsAFreshList() {
        // Arrange
        List<String> first = PasswordPolicy.violations("");
        first.clear();

        // Act
        List<String> second = PasswordPolicy.violations("");

        // Assert
        assertFalse(second.isEmpty(),
                "Mutating a previously returned list must not affect later calls");
    }
}
