package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    @DisplayName("Strong password passes the policy")
    void strongPasswordIsValid() {
        // Act
        boolean valid = PasswordPolicy.isValid("Str0ng!Pass");

        // Assert
        assertTrue(valid);
    }

    @Test
    @DisplayName("Short password is rejected")
    void shortPasswordIsRejected() {
        // Act
        List<String> violations = PasswordPolicy.violations("Ab1!");

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Password without required character classes reports every missing rule")
    void reportsAllMissingRules() {
        // Act
        List<String> violations = PasswordPolicy.violations("todolowercase");

        // Assert
        assertEqualsAtLeast(violations, 3);
    }

    @Test
    @DisplayName("Null password is rejected without NPE")
    void nullPasswordIsRejected() {
        // Act
        boolean valid = PasswordPolicy.isValid(null);

        // Assert
        assertFalse(valid);
    }

    private void assertEqualsAtLeast(List<String> violations, int minimum) {
        assertTrue(violations.size() >= minimum,
                "Expected at least " + minimum + " violations but got " + violations);
    }

    // ----------------------------------------------------------------------------------------
    // Length: boundary value analysis around MIN_LENGTH (8) and MAX_LENGTH (72)
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Length rule")
    class LengthRule {

        @Test
        @DisplayName("The policy declares 8 and 72 as the accepted length boundaries")
        void boundariesAreEightAndSeventyTwo() {
            // Act
            int minLength = PasswordPolicy.MIN_LENGTH;
            int maxLength = PasswordPolicy.MAX_LENGTH;

            // Assert
            assertEquals(8, minLength);
            assertEquals(72, maxLength);
        }

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
        @ValueSource(ints = {73, 100})
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
        @DisplayName("Exactly 72 characters is the last accepted length")
        void maximumBoundaryIsInclusive() {
            // Arrange
            String password = passwordOfLength(72);

            // Act
            boolean valid = PasswordPolicy.isValid(password);

            // Assert
            assertTrue(valid);
        }

        @Test
        @DisplayName("73 characters is the first rejected length above the maximum")
        void firstLengthAboveMaximumIsInvalid() {
            // Arrange
            String password = passwordOfLength(73);

            // Act
            boolean valid = PasswordPolicy.isValid(password);

            // Assert
            assertFalse(valid);
        }

        @Test
        @DisplayName("Exactly 8 characters is the first accepted length")
        void minimumBoundaryIsInclusive() {
            // Arrange
            String password = passwordOfLength(8);

            // Act
            boolean valid = PasswordPolicy.isValid(password);

            // Assert
            assertTrue(valid);
        }

        @Test
        @DisplayName("7 characters is the last rejected length below the minimum")
        void lastLengthBelowMinimumIsInvalid() {
            // Arrange
            String password = passwordOfLength(7);

            // Act
            boolean valid = PasswordPolicy.isValid(password);

            // Assert
            assertFalse(valid);
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

        @Test
        @DisplayName("An empty password reports the four character-class violations, the only input that can break all four")
        void emptyPasswordReportsTheFourCharacterClassViolations() {
            // Act
            List<String> violations = PasswordPolicy.violations("");

            // Assert
            assertTrue(violations.containsAll(List.of(NO_UPPER, NO_LOWER, NO_DIGIT, NO_SPECIAL)),
                    () -> "Expected the four character-class violations, got " + violations);
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

        @Test
        @DisplayName("A blank password made only of spaces still fails upper, lower and digit but not the special rule")
        void blankPasswordFailsEveryRuleButSpecial() {
            // Act
            List<String> violations = PasswordPolicy.violations("         ");

            // Assert
            assertIterableEquals(List.of(NO_UPPER, NO_LOWER, NO_DIGIT), violations);
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
                "Contraseña1",
                "Passw0rd here",
                "Aa1€€€€€",
                "Aa1¡¿test",
                "Aa1_underscore",
                "Aa1\tTabbed"
        })
        @DisplayName("Any non-alphanumeric character counts as special, including accents, symbols, spaces and tabs")
        void nonAlphanumericCharactersCountAsSpecial(String password) {
            // Act
            List<String> violations = PasswordPolicy.violations(password);

            // Assert
            assertIterableEquals(List.of(), violations,
                    () -> "Expected \"" + password + "\" to be accepted");
        }

        @Test
        @DisplayName("A space alone satisfies the special-character requirement")
        void spaceCountsAsSpecialCharacter() {
            // Act
            boolean valid = PasswordPolicy.isValid("Passw0rd here");

            // Assert
            assertTrue(valid);
        }

        @Test
        @DisplayName("An accented letter satisfies the special-character requirement because the rule is non-ASCII-alphanumeric")
        void accentedLetterCountsAsSpecialCharacter() {
            // Act
            boolean valid = PasswordPolicy.isValid("Contraseña1");

            // Assert
            assertTrue(valid);
        }

        @Test
        @DisplayName("Uppercase and lowercase rules only accept ASCII letters, so a non-ASCII-only password fails both")
        void nonAsciiLettersDoNotSatisfyTheCaseRules() {
            // Act
            List<String> violations = PasswordPolicy.violations("ÑÑññÑÑññ1");

            // Assert
            assertIterableEquals(List.of(NO_UPPER, NO_LOWER), violations);
        }
    }

    // ----------------------------------------------------------------------------------------
    // isValid must always agree with violations().isEmpty()
    // ----------------------------------------------------------------------------------------

    static Stream<String> passwordCorpus() {
        return Stream.of(
                null,
                "",
                " ",
                "Aa1!",
                passwordOfLength(7),
                passwordOfLength(8),
                passwordOfLength(71),
                passwordOfLength(72),
                passwordOfLength(73),
                "str0ng!pass",
                "STR0NG!PASS",
                "Strong!Pass",
                "Str0ngPassw",
                "todolowercase",
                "Contraseña1",
                "Passw0rd here",
                "12345678",
                "!!!!!!!!",
                "Str0ng!Pass");
    }

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

    @Test
    @DisplayName("PasswordPolicy is a utility class that cannot be instantiated")
    void policyCannotBeInstantiated() throws Exception {
        // Arrange
        Constructor<PasswordPolicy> constructor = PasswordPolicy.class.getDeclaredConstructor();

        // Act
        int modifiers = constructor.getModifiers();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPrivate(modifiers),
                "The only constructor must be private");
    }

    @Test
    @DisplayName("The private constructor still builds an instance when forced through reflection")
    void privateConstructorBuildsAnInstanceReflectively() throws Exception {
        // Arrange
        Constructor<PasswordPolicy> constructor = PasswordPolicy.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // Act
        PasswordPolicy instance = constructor.newInstance();

        // Assert
        assertEquals(PasswordPolicy.class, instance.getClass());
    }

    @Test
    @DisplayName("Evaluating a null password never throws a NullPointerException")
    void nullPasswordNeverThrows() {
        // Act
        Executable act = () -> PasswordPolicy.violations(null);

        // Assert
        assertDoesNotThrow(act);
    }
}
