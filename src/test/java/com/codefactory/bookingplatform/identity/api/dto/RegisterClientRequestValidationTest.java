package com.codefactory.bookingplatform.identity.api.dto;

import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import static com.codefactory.bookingplatform.support.BeanValidationSupport.digits;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.emailOfLength;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.invalidProperties;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.messagesFor;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.repeat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black box boundary analysis of the registration payload, run straight against a
 * Jakarta Validator. Every constraint declared on {@link RegisterClientRequest} is
 * pinned on both sides of its limit (valid / invalid equivalence classes).
 */
class RegisterClientRequestValidationTest {

    private static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(1995, 4, 10);

    private static RegisterClientRequest valid() {
        return new RegisterClientRequest(
                "Ana Maria Perez",
                "CC-1020304050",
                ADULT_BIRTH_DATE,
                "ana.perez@example.com",
                "+573001234567",
                "Bogota",
                NotificationChannel.EMAIL,
                "Str0ng!Pass");
    }

    private static RegisterClientRequest withFullName(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(value, v.document(), v.birthDate(), v.email(),
                v.phone(), v.city(), v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withDocument(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), value, v.birthDate(), v.email(),
                v.phone(), v.city(), v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withBirthDate(LocalDate value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), value, v.email(),
                v.phone(), v.city(), v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withEmail(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), v.birthDate(), value,
                v.phone(), v.city(), v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withPhone(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), v.birthDate(), v.email(),
                value, v.city(), v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withCity(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), v.birthDate(), v.email(),
                v.phone(), value, v.notificationChannel(), v.password());
    }

    private static RegisterClientRequest withChannel(NotificationChannel value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), v.birthDate(), v.email(),
                v.phone(), v.city(), value, v.password());
    }

    private static RegisterClientRequest withPassword(String value) {
        RegisterClientRequest v = valid();
        return new RegisterClientRequest(v.fullName(), v.document(), v.birthDate(), v.email(),
                v.phone(), v.city(), v.notificationChannel(), value);
    }

    @Test
    @DisplayName("The reference payload raises no violation at all")
    void referencePayloadIsValid() {
        // Arrange
        RegisterClientRequest request = valid();

        // Act
        Set<String> invalid = invalidProperties(request);

        // Assert
        assertEquals(Set.of(), invalid);
    }

    @Nested
    @DisplayName("fullName: NotBlank + Size(max = 120)")
    class FullName {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {1, 119, 120})
        void acceptedLengths(int length) {
            // Arrange
            RegisterClientRequest request = withFullName(repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "fullName");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("121 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            // Arrange
            RegisterClientRequest request = withFullName(repeat('a', 121));

            // Act
            Set<String> messages = messagesFor(request, "fullName");

            // Assert
            assertEquals(Set.of("fullName must be at most 120 characters"), messages);
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withFullName(value);

            // Act
            Set<String> messages = messagesFor(request, "fullName");

            // Assert
            assertTrue(messages.contains("fullName is required"));
        }
    }

    @Nested
    @DisplayName("document: NotBlank + Pattern [A-Za-z0-9-]{5,20}")
    class Document {

        @ParameterizedTest(name = "[{0}] is accepted")
        @ValueSource(strings = {"ABCDE", "12345", "CC-1020304050", "ABCDEFGHIJ1234567890", "-----"})
        void acceptedDocuments(String value) {
            // Arrange
            RegisterClientRequest request = withDocument(value);

            // Act
            Set<String> messages = messagesFor(request, "document");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @ParameterizedTest(name = "[{0}] is rejected")
        @ValueSource(strings = {
                "ABCD",
                "ABCDEFGHIJ12345678901",
                "ABC DE",
                "ABC_DE",
                "ABCD.E"})
        void rejectedDocuments(String value) {
            // Arrange
            RegisterClientRequest request = withDocument(value);

            // Act
            Set<String> messages = messagesFor(request, "document");

            // Assert
            assertEquals(Set.of("document must be 5-20 alphanumeric characters or hyphens"), messages);
        }

        @Test
        @DisplayName("An accented letter is outside the allowed character set")
        void accentedLetterIsRejected() {
            // Arrange
            RegisterClientRequest request = withDocument("ABCDÉ");

            // Act
            Set<String> messages = messagesFor(request, "document");

            // Assert
            assertEquals(Set.of("document must be 5-20 alphanumeric characters or hyphens"), messages);
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withDocument(value);

            // Act
            Set<String> messages = messagesFor(request, "document");

            // Assert
            assertTrue(messages.contains("document is required"));
        }
    }

    @Nested
    @DisplayName("birthDate: NotNull + Past")
    class BirthDate {

        @Test
        @DisplayName("Yesterday is in the past and is accepted")
        void yesterdayIsAccepted() {
            // Arrange
            RegisterClientRequest request = withBirthDate(LocalDate.now().minusDays(1));

            // Act
            Set<String> messages = messagesFor(request, "birthDate");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("Today is NOT in the past and is rejected")
        void todayIsRejected() {
            // Arrange
            RegisterClientRequest request = withBirthDate(LocalDate.now());

            // Act
            Set<String> messages = messagesFor(request, "birthDate");

            // Assert
            assertEquals(Set.of("birthDate must be in the past"), messages);
        }

        @Test
        @DisplayName("Tomorrow is rejected")
        void futureIsRejected() {
            // Arrange
            RegisterClientRequest request = withBirthDate(LocalDate.now().plusDays(1));

            // Act
            Set<String> messages = messagesFor(request, "birthDate");

            // Assert
            assertEquals(Set.of("birthDate must be in the past"), messages);
        }

        @Test
        @DisplayName("A null birthDate is rejected as required")
        void nullIsRejected() {
            // Arrange
            RegisterClientRequest request = withBirthDate(null);

            // Act
            Set<String> messages = messagesFor(request, "birthDate");

            // Assert
            assertEquals(Set.of("birthDate is required"), messages);
        }
    }

    @Nested
    @DisplayName("email: NotBlank + Email + Size(max = 160)")
    class Email {

        @Test
        @DisplayName("An address of exactly 160 characters is accepted")
        void maxLengthIsAccepted() {
            // Arrange
            RegisterClientRequest request = withEmail(emailOfLength(160));

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("An address of 161 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            // Arrange
            RegisterClientRequest request = withEmail(emailOfLength(161));

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertEquals(Set.of("email must be at most 160 characters"), messages);
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "missing-at.example.com", "a@", "@example.com", "a b@example.com"})
        void invalidFormatsAreRejected(String value) {
            // Arrange
            RegisterClientRequest request = withEmail(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email must be a valid address"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withEmail(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email is required"));
        }
    }

    @Nested
    @DisplayName("phone: NotBlank + Pattern optional plus then 7-15 digits")
    class Phone {

        static Stream<Arguments> accepted() {
            return Stream.of(
                    Arguments.of(digits(7)),
                    Arguments.of(digits(15)),
                    Arguments.of("+" + digits(7)),
                    Arguments.of("+" + digits(15)));
        }

        @ParameterizedTest(name = "[{0}] is accepted")
        @MethodSource("accepted")
        void acceptedPhones(String value) {
            // Arrange
            RegisterClientRequest request = withPhone(value);

            // Act
            Set<String> messages = messagesFor(request, "phone");

            // Assert
            assertTrue(messages.isEmpty());
        }

        static Stream<Arguments> rejected() {
            return Stream.of(
                    Arguments.of(digits(6)),
                    Arguments.of(digits(16)),
                    Arguments.of("+" + digits(6)),
                    Arguments.of("+" + digits(16)),
                    Arguments.of("300abc4567"),
                    Arguments.of("+57 300 1234567"),
                    Arguments.of("++5730012345"),
                    Arguments.of("3001234567+"));
        }

        @ParameterizedTest(name = "[{0}] is rejected")
        @MethodSource("rejected")
        void rejectedPhones(String value) {
            // Arrange
            RegisterClientRequest request = withPhone(value);

            // Act
            Set<String> messages = messagesFor(request, "phone");

            // Assert
            assertEquals(Set.of("phone must contain 7-15 digits, optionally prefixed with +"), messages);
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withPhone(value);

            // Act
            Set<String> messages = messagesFor(request, "phone");

            // Assert
            assertTrue(messages.contains("phone is required"));
        }
    }

    @Nested
    @DisplayName("city: NotBlank + Size(max = 80)")
    class City {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {1, 79, 80})
        void acceptedLengths(int length) {
            // Arrange
            RegisterClientRequest request = withCity(repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "city");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("81 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            // Arrange
            RegisterClientRequest request = withCity(repeat('a', 81));

            // Act
            Set<String> messages = messagesFor(request, "city");

            // Assert
            assertEquals(Set.of("city must be at most 80 characters"), messages);
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withCity(value);

            // Act
            Set<String> messages = messagesFor(request, "city");

            // Assert
            assertTrue(messages.contains("city is required"));
        }
    }

    @Nested
    @DisplayName("password: NotBlank + Size(min = 8, max = 72)")
    class Password {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        void acceptedLengths(int length) {
            // Arrange
            RegisterClientRequest request = withPassword(repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @ParameterizedTest(name = "length {0} is outside [8, 72] and is rejected")
        @CsvSource({"1", "7", "73", "100"})
        void rejectedLengths(int length) {
            // Arrange
            RegisterClientRequest request = withPassword(repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertTrue(messages.contains("password must be between 8 and 72 characters"));
        }

        @ParameterizedTest(name = "missing or empty value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {""})
        void mandatoryValueIsRejected(String value) {
            // Arrange
            RegisterClientRequest request = withPassword(value);

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertFalse(messages.isEmpty());
        }

        @Test
        @DisplayName("A password of eight spaces is long enough but still blank")
        void eightSpacesIsBlank() {
            // Arrange
            RegisterClientRequest request = withPassword("        ");

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertEquals(Set.of("password is required"), messages);
        }
    }

    @Nested
    @DisplayName("notificationChannel: NotNull")
    class Channel {

        @ParameterizedTest(name = "{0} is accepted")
        @ValueSource(strings = {"EMAIL", "SMS", "WHATSAPP"})
        void everyDeclaredChannelIsAccepted(String value) {
            // Arrange
            RegisterClientRequest request = withChannel(NotificationChannel.valueOf(value));

            // Act
            Set<String> messages = messagesFor(request, "notificationChannel");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("A null channel is rejected as required")
        void nullIsRejected() {
            // Arrange
            RegisterClientRequest request = withChannel(null);

            // Act
            Set<String> messages = messagesFor(request, "notificationChannel");

            // Assert
            assertEquals(Set.of("notificationChannel is required"), messages);
        }
    }

    @Test
    @DisplayName("An all-null payload reports every mandatory field at once")
    void allNullPayloadReportsEveryMandatoryField() {
        // Arrange
        RegisterClientRequest empty = new RegisterClientRequest(null, null, null, null, null, null, null, null);

        // Act
        Set<String> invalid = invalidProperties(empty);

        // Assert
        assertEquals(
                Set.of("fullName", "document", "birthDate", "email", "phone", "city", "notificationChannel", "password"),
                invalid);
    }
}
