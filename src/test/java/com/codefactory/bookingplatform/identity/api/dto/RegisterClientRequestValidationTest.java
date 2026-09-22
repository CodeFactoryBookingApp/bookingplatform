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
        assertEquals(Set.of(), invalidProperties(valid()));
    }

    @Nested
    @DisplayName("fullName: NotBlank + Size(max = 120)")
    class FullName {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {1, 119, 120})
        void acceptedLengths(int length) {
            assertTrue(messagesFor(withFullName(repeat('a', length)), "fullName").isEmpty());
        }

        @Test
        @DisplayName("121 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            assertEquals(Set.of("fullName must be at most 120 characters"),
                    messagesFor(withFullName(repeat('a', 121)), "fullName"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            assertTrue(messagesFor(withFullName(value), "fullName").contains("fullName is required"));
        }
    }

    @Nested
    @DisplayName("document: NotBlank + Pattern [A-Za-z0-9-]{5,20}")
    class Document {

        @ParameterizedTest(name = "[{0}] is accepted")
        @ValueSource(strings = {"ABCDE", "12345", "CC-1020304050", "ABCDEFGHIJ1234567890", "-----"})
        void acceptedDocuments(String value) {
            assertTrue(messagesFor(withDocument(value), "document").isEmpty());
        }

        @ParameterizedTest(name = "[{0}] is rejected")
        @ValueSource(strings = {
                "ABCD",
                "ABCDEFGHIJ12345678901",
                "ABC DE",
                "ABC_DE",
                "ABCD.E"})
        void rejectedDocuments(String value) {
            assertEquals(Set.of("document must be 5-20 alphanumeric characters or hyphens"),
                    messagesFor(withDocument(value), "document"));
        }

        @Test
        @DisplayName("An accented letter is outside the allowed character set")
        void accentedLetterIsRejected() {
            assertEquals(Set.of("document must be 5-20 alphanumeric characters or hyphens"),
                    messagesFor(withDocument("ABCDÉ"), "document"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            assertTrue(messagesFor(withDocument(value), "document").contains("document is required"));
        }
    }

    @Nested
    @DisplayName("birthDate: NotNull + Past")
    class BirthDate {

        @Test
        @DisplayName("Yesterday is in the past and is accepted")
        void yesterdayIsAccepted() {
            assertTrue(messagesFor(withBirthDate(LocalDate.now().minusDays(1)), "birthDate").isEmpty());
        }

        @Test
        @DisplayName("Today is NOT in the past and is rejected")
        void todayIsRejected() {
            assertEquals(Set.of("birthDate must be in the past"),
                    messagesFor(withBirthDate(LocalDate.now()), "birthDate"));
        }

        @Test
        @DisplayName("Tomorrow is rejected")
        void futureIsRejected() {
            assertEquals(Set.of("birthDate must be in the past"),
                    messagesFor(withBirthDate(LocalDate.now().plusDays(1)), "birthDate"));
        }

        @Test
        @DisplayName("A null birthDate is rejected as required")
        void nullIsRejected() {
            assertEquals(Set.of("birthDate is required"), messagesFor(withBirthDate(null), "birthDate"));
        }
    }

    @Nested
    @DisplayName("email: NotBlank + Email + Size(max = 160)")
    class Email {

        @Test
        @DisplayName("An address of exactly 160 characters is accepted")
        void maxLengthIsAccepted() {
            assertTrue(messagesFor(withEmail(emailOfLength(160)), "email").isEmpty());
        }

        @Test
        @DisplayName("An address of 161 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            assertEquals(Set.of("email must be at most 160 characters"),
                    messagesFor(withEmail(emailOfLength(161)), "email"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "missing-at.example.com", "a@", "@example.com", "a b@example.com"})
        void invalidFormatsAreRejected(String value) {
            assertTrue(messagesFor(withEmail(value), "email").contains("email must be a valid address"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            assertTrue(messagesFor(withEmail(value), "email").contains("email is required"));
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
            assertTrue(messagesFor(withPhone(value), "phone").isEmpty());
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
            assertEquals(Set.of("phone must contain 7-15 digits, optionally prefixed with +"),
                    messagesFor(withPhone(value), "phone"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            assertTrue(messagesFor(withPhone(value), "phone").contains("phone is required"));
        }
    }

    @Nested
    @DisplayName("city: NotBlank + Size(max = 80)")
    class City {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {1, 79, 80})
        void acceptedLengths(int length) {
            assertTrue(messagesFor(withCity(repeat('a', length)), "city").isEmpty());
        }

        @Test
        @DisplayName("81 characters is one over the limit and is rejected")
        void oneOverTheLimitIsRejected() {
            assertEquals(Set.of("city must be at most 80 characters"),
                    messagesFor(withCity(repeat('a', 81)), "city"));
        }

        @ParameterizedTest(name = "missing, empty or blank value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void mandatoryValueIsRejected(String value) {
            assertTrue(messagesFor(withCity(value), "city").contains("city is required"));
        }
    }

    @Nested
    @DisplayName("password: NotBlank + Size(min = 8, max = 72)")
    class Password {

        @ParameterizedTest(name = "length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        void acceptedLengths(int length) {
            assertTrue(messagesFor(withPassword(repeat('a', length)), "password").isEmpty());
        }

        @ParameterizedTest(name = "length {0} is outside [8, 72] and is rejected")
        @CsvSource({"1", "7", "73", "100"})
        void rejectedLengths(int length) {
            assertTrue(messagesFor(withPassword(repeat('a', length)), "password")
                    .contains("password must be between 8 and 72 characters"));
        }

        @ParameterizedTest(name = "missing or empty value is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {""})
        void mandatoryValueIsRejected(String value) {
            assertFalse(messagesFor(withPassword(value), "password").isEmpty());
        }

        @Test
        @DisplayName("A password of eight spaces is long enough but still blank")
        void eightSpacesIsBlank() {
            assertEquals(Set.of("password is required"), messagesFor(withPassword("        "), "password"));
        }
    }

    @Nested
    @DisplayName("notificationChannel: NotNull")
    class Channel {

        @ParameterizedTest(name = "{0} is accepted")
        @ValueSource(strings = {"EMAIL", "SMS", "WHATSAPP"})
        void everyDeclaredChannelIsAccepted(String value) {
            assertTrue(messagesFor(withChannel(NotificationChannel.valueOf(value)), "notificationChannel").isEmpty());
        }

        @Test
        @DisplayName("A null channel is rejected as required")
        void nullIsRejected() {
            assertEquals(Set.of("notificationChannel is required"),
                    messagesFor(withChannel(null), "notificationChannel"));
        }
    }

    @Test
    @DisplayName("An all-null payload reports every mandatory field at once")
    void allNullPayloadReportsEveryMandatoryField() {
        RegisterClientRequest empty = new RegisterClientRequest(null, null, null, null, null, null, null, null);
        assertEquals(
                Set.of("fullName", "document", "birthDate", "email", "phone", "city", "notificationChannel", "password"),
                invalidProperties(empty));
    }
}
