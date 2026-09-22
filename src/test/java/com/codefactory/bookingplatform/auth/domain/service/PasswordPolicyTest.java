package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertTrue(PasswordPolicy.isValid("Str0ng!Pass"));
    }

    @Test
    @DisplayName("Short password is rejected")
    void shortPasswordIsRejected() {
        List<String> violations = PasswordPolicy.violations("Ab1!");
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Password without required character classes reports every missing rule")
    void reportsAllMissingRules() {
        List<String> violations = PasswordPolicy.violations("todolowercase");
        assertEqualsAtLeast(violations, 3);
    }

    @Test
    @DisplayName("Null password is rejected without NPE")
    void nullPasswordIsRejected() {
        assertFalse(PasswordPolicy.isValid(null));
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
            assertEquals(8, PasswordPolicy.MIN_LENGTH);
            assertEquals(72, PasswordPolicy.MAX_LENGTH);
        }

        @ParameterizedTest(name = "length {0} is rejected as too short")
        @ValueSource(ints = {1, 7})
        @DisplayName("A password shorter than 8 characters is rejected for length even if it has every character class")
        void belowMinimumLengthIsRejected(int length) {
            List<String> violations = PasswordPolicy.violations(passwordOfLength(length));
            assertTrue(violations.contains(TOO_SHORT), () -> "Expected the too-short violation, got " + violations);
        }

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        @DisplayName("A password between 8 and 72 characters that meets every character class is accepted")
        void lengthsInsideTheRangeAreAccepted(int length) {
            String password = passwordOfLength(length);
            assertIterableEquals(List.of(), PasswordPolicy.violations(password),
                    () -> "Expected no violations for a valid password of length " + length);
        }

        @ParameterizedTest(name = "length {0} is rejected as too long")
        @ValueSource(ints = {73, 100})
        @DisplayName("A password longer than 72 characters is rejected for length")
        void aboveMaximumLengthIsRejected(int length) {
            List<String> violations = PasswordPolicy.violations(passwordOfLength(length));
            assertTrue(violations.contains(TOO_LONG), () -> "Expected the too-long violation, got " + violations);
        }

        @Test
        @DisplayName("Exactly 72 characters is the last accepted length and 73 is the first rejected one")
        void maximumBoundaryIsInclusive() {
            assertTrue(PasswordPolicy.isValid(passwordOfLength(72)));
            assertFalse(PasswordPolicy.isValid(passwordOfLength(73)));
        }

        @Test
        @DisplayName("Exactly 8 characters is the first accepted length and 7 is the last rejected one")
        void minimumBoundaryIsInclusive() {
            assertTrue(PasswordPolicy.isValid(passwordOfLength(8)));
            assertFalse(PasswordPolicy.isValid(passwordOfLength(7)));
        }

        @Test
        @DisplayName("An over-long password whose only defect is the length reports just that one violation")
        void tooLongReportsOnlyTheLengthViolation() {
            assertIterableEquals(List.of(TOO_LONG), PasswordPolicy.violations(passwordOfLength(73)));
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
            assertIterableEquals(List.of(TOO_SHORT), PasswordPolicy.violations(null));
        }

        @Test
        @DisplayName("An empty password breaks the length rule and all four character-class rules at once")
        void emptyPasswordBreaksEveryRule() {
            List<String> violations = PasswordPolicy.violations("");
            assertIterableEquals(List.of(TOO_SHORT, NO_UPPER, NO_LOWER, NO_DIGIT, NO_SPECIAL), violations);
        }

        @Test
        @DisplayName("An empty password reports the four character-class violations, the only input that can break all four")
        void emptyPasswordReportsTheFourCharacterClassViolations() {
            List<String> violations = PasswordPolicy.violations("");
            assertTrue(violations.containsAll(List.of(NO_UPPER, NO_LOWER, NO_DIGIT, NO_SPECIAL)),
                    () -> "Expected the four character-class violations, got " + violations);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Neither null nor an empty password is ever valid")
        void nullAndEmptyAreNeverValid(String password) {
            assertFalse(PasswordPolicy.isValid(password));
        }

        @Test
        @DisplayName("A blank password made only of spaces still fails upper, lower and digit but not the special rule")
        void blankPasswordFailsEveryRuleButSpecial() {
            List<String> violations = PasswordPolicy.violations("         ");
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
            assertIterableEquals(List.of(expectedViolation), PasswordPolicy.violations(password));
        }

        @Test
        @DisplayName("A password missing three character classes reports the three of them in rule order")
        void threeMissingClassesAreAllReported() {
            assertIterableEquals(List.of(NO_UPPER, NO_DIGIT, NO_SPECIAL), PasswordPolicy.violations("todolowercase"));
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
            assertIterableEquals(List.of(), PasswordPolicy.violations(password),
                    () -> "Expected \"" + password + "\" to be accepted");
        }

        @Test
        @DisplayName("A space alone satisfies the special-character requirement")
        void spaceCountsAsSpecialCharacter() {
            assertTrue(PasswordPolicy.isValid("Passw0rd here"));
        }

        @Test
        @DisplayName("An accented letter satisfies the special-character requirement because the rule is non-ASCII-alphanumeric")
        void accentedLetterCountsAsSpecialCharacter() {
            assertTrue(PasswordPolicy.isValid("Contraseña1"));
        }

        @Test
        @DisplayName("Uppercase and lowercase rules only accept ASCII letters, so a non-ASCII-only password fails both")
        void nonAsciiLettersDoNotSatisfyTheCaseRules() {
            List<String> violations = PasswordPolicy.violations("ÑÑññÑÑññ1");
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

    @ParameterizedTest(name = "isValid and violations agree for [{0}]")
    @MethodSource("passwordCorpus")
    @DisplayName("isValid is true exactly when the violation list is empty")
    void isValidAgreesWithViolations(String password) {
        assertEquals(PasswordPolicy.violations(password).isEmpty(), PasswordPolicy.isValid(password),
                () -> "isValid disagreed with violations() for [" + password + "]");
    }

    @Test
    @DisplayName("The returned violation list is a fresh list on every call, so callers cannot poison the policy")
    void violationsReturnsAFreshList() {
        List<String> first = PasswordPolicy.violations("");
        first.clear();
        assertFalse(PasswordPolicy.violations("").isEmpty(),
                "Mutating a previously returned list must not affect later calls");
    }

    @Test
    @DisplayName("PasswordPolicy is a utility class that cannot be instantiated")
    void policyCannotBeInstantiated() throws Exception {
        var constructor = PasswordPolicy.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
                "The only constructor must be private");
        constructor.setAccessible(true);
        assertEquals(PasswordPolicy.class, constructor.newInstance().getClass());
    }

    @Test
    @DisplayName("Evaluating a null password never throws a NullPointerException")
    void nullPasswordNeverThrows() {
        assertDoesNotThrow(() -> PasswordPolicy.violations(null));
    }
}
