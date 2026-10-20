package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for a rejected business rule. It pairs an {@link ErrorCode} with a message and an
 * optional detail map that the exception handler publishes to the client, so the detail map must
 * be a defensive, unmodifiable copy: it crosses layers and is serialised.
 *
 * <p>Black box: one partition per constructor plus the {@code of} factory. White box: the three
 * constructors all funnel into the canonical one, so the copy and the wrapping are exercised for
 * every entry point.</p>
 */
class BusinessExceptionTest {

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        @DisplayName("The code-only constructor falls back to the default message of the error code")
        void codeOnlyUsesTheDefaultMessage() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Assert
            assertEquals(ErrorCode.DUPLICATE_EMAIL.defaultMessage(), ex.getMessage());
        }

        @Test
        @DisplayName("The code-only constructor keeps the error code")
        void codeOnlyKeepsTheErrorCode() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Assert
            assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.errorCode());
        }

        @Test
        @DisplayName("The code-only constructor leaves the detail map empty, so no extra field reaches the client")
        void codeOnlyHasNoDetails() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.DUPLICATE_EMAIL);

            // Assert
            assertTrue(ex.details().isEmpty());
        }

        @Test
        @DisplayName("The code-and-message constructor overrides the default message")
        void codeAndMessageOverridesTheDefaultMessage() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "too short");

            // Assert
            assertEquals("too short", ex.getMessage());
        }

        @Test
        @DisplayName("The code-and-message constructor leaves the detail map empty")
        void codeAndMessageHasNoDetails() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "too short");

            // Assert
            assertTrue(ex.details().isEmpty());
        }

        @Test
        @DisplayName("The full constructor keeps the code, the message and the details together")
        void fullConstructorKeepsEverything() {
            // Arrange
            Map<String, String> details = Map.of("email", "must not be blank");

            // Act
            BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid payload", details);

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals("invalid payload", ex.getMessage());
            assertEquals(Map.of("email", "must not be blank"), ex.details());
        }

        @Test
        @DisplayName("The full constructor preserves the insertion order of the details, which is the order shown to the client")
        void fullConstructorPreservesDetailOrder() {
            // Arrange
            Map<String, String> ordered = new LinkedHashMap<>();
            ordered.put("first", "1");
            ordered.put("second", "2");
            ordered.put("third", "3");

            // Act
            BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid", ordered);

            // Assert
            assertIterableEquals(List.of("first", "second", "third"), ex.details().keySet());
        }

        @Test
        @DisplayName("An explicitly null message is kept as null instead of being replaced by the default one")
        void nullMessageIsNotReplaced() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED, null);

            // Assert
            assertNull(ex.getMessage());
        }

        @Test
        @DisplayName("An empty detail map produces an empty detail map, not a null one")
        void emptyDetailsStayEmpty() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED, "denied", Map.of());

            // Assert
            assertTrue(ex.details().isEmpty());
        }

        @ParameterizedTest(name = "{0} can be carried")
        @EnumSource(ErrorCode.class)
        @DisplayName("Any error code in the catalogue can be carried by the exception")
        void anyErrorCodeCanBeCarried(ErrorCode code) {
            // Act
            BusinessException ex = new BusinessException(code);

            // Assert
            assertEquals(code, ex.errorCode());
        }

        @Test
        @DisplayName("The exception is unchecked so use cases can reject a rule without declaring it")
        void exceptionIsUnchecked() {
            // Act
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Assert
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    @Nested
    @DisplayName("Java serialisation")
    class Serialisation {

        private static BusinessException roundTrip(BusinessException original) throws Exception {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(original);
            }
            try (java.io.ObjectInputStream in =
                         new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                return (BusinessException) in.readObject();
            }
        }

        @Test
        @DisplayName("The exception declares an explicit serialVersionUID so a redeploy cannot change it")
        void declaresAnExplicitSerialVersionUid() throws Exception {
            // Arrange
            java.lang.reflect.Field field = BusinessException.class.getDeclaredField("serialVersionUID");
            field.setAccessible(true);

            // Act
            long serialVersionUid = field.getLong(null);

            // Assert
            assertEquals(1L, serialVersionUid);
        }

        @Test
        @DisplayName("A serialised rejection keeps its error code and message")
        void roundTripKeepsCodeAndMessage() throws Exception {
            // Arrange
            BusinessException original = new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "document already used");

            // Act
            BusinessException restored = roundTrip(original);

            // Assert
            assertEquals(ErrorCode.DUPLICATE_DOCUMENT, restored.errorCode());
            assertEquals("document already used", restored.getMessage());
        }

        @Test
        @DisplayName("A serialised rejection keeps its details: the detail map is not transient")
        void roundTripKeepsTheDetails() throws Exception {
            // Arrange
            Map<String, String> ordered = new LinkedHashMap<>();
            ordered.put("first", "1");
            ordered.put("second", "2");
            BusinessException original = new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid", ordered);

            // Act
            BusinessException restored = roundTrip(original);

            // Assert
            assertEquals(ordered, restored.details());
            assertIterableEquals(List.of("first", "second"), restored.details().keySet());
        }

        @Test
        @DisplayName("The details published by a deserialised rejection are still unmodifiable")
        void roundTripKeepsTheDetailsUnmodifiable() throws Exception {
            // Arrange
            BusinessException restored = roundTrip(
                    new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid", Map.of("email", "blank")));

            // Act
            Executable act = () -> restored.details().put("k", "v");

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }
    }

    @Nested
    @DisplayName("of factory")
    class OfFactory {

        @Test
        @DisplayName("The factory builds an exception with exactly one detail entry")
        void factoryBuildsASingleDetailEntry() {
            // Act
            BusinessException ex = BusinessException.of(
                    ErrorCode.DUPLICATE_DOCUMENT, "document already used", "documentNumber", "1017245896");

            // Assert
            assertEquals(Map.of("documentNumber", "1017245896"), ex.details());
        }

        @Test
        @DisplayName("The factory keeps the error code and the message it was given")
        void factoryKeepsCodeAndMessage() {
            // Act
            BusinessException ex = BusinessException.of(
                    ErrorCode.DUPLICATE_DOCUMENT, "document already used", "documentNumber", "1017245896");

            // Assert
            assertEquals(ErrorCode.DUPLICATE_DOCUMENT, ex.errorCode());
            assertEquals("document already used", ex.getMessage());
        }

        @Test
        @DisplayName("The detail map built by the factory is unmodifiable like any other")
        void factoryDetailsAreUnmodifiable() {
            // Arrange
            BusinessException ex = BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "no such booking", "id", "42");

            // Act
            Executable act = () -> ex.details().put("other", "x");

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }

        @Test
        @DisplayName("A null detail key is rejected, because the factory relies on an immutable map")
        void factoryRejectsANullDetailKey() {
            // Act
            Executable act = () -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, "missing", null, "42");

            // Assert
            assertThrows(NullPointerException.class, act);
        }
    }

    @Nested
    @DisplayName("Detail map immutability")
    class DetailMapImmutability {

        @Test
        @DisplayName("Adding an entry to the published details is rejected, so the exception cannot be altered downstream")
        void detailsRejectInsertion() {
            // Arrange
            BusinessException ex = new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "invalid", Map.of("email", "must not be blank"));

            // Act
            Executable act = () -> ex.details().put("password", "too weak");

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }

        @Test
        @DisplayName("Removing an entry from the published details is rejected")
        void detailsRejectRemoval() {
            // Arrange
            BusinessException ex = new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "invalid", Map.of("email", "must not be blank"));

            // Act
            Executable act = () -> ex.details().remove("email");

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }

        @Test
        @DisplayName("Clearing the published details is rejected")
        void detailsRejectClear() {
            // Arrange
            BusinessException ex = new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "invalid", Map.of("email", "must not be blank"));

            // Act
            Executable act = () -> ex.details().clear();

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }

        @Test
        @DisplayName("An empty detail map is unmodifiable too")
        void emptyDetailsAreUnmodifiable() {
            // Arrange
            BusinessException ex = new BusinessException(ErrorCode.ACCESS_DENIED);

            // Act
            Executable act = () -> ex.details().put("k", "v");

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }

        @Test
        @DisplayName("The exception copies the caller map, so adding to the original afterwards does not leak into the exception")
        void mutatingTheSourceMapDoesNotAffectTheException() {
            // Arrange
            Map<String, String> source = new HashMap<>();
            source.put("email", "must not be blank");
            BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid", source);

            // Act
            source.put("password", "too weak");

            // Assert
            assertEquals(Map.of("email", "must not be blank"), ex.details());
        }

        @Test
        @DisplayName("Clearing the caller map afterwards does not empty the details already carried by the exception")
        void clearingTheSourceMapDoesNotEmptyTheException() {
            // Arrange
            Map<String, String> source = new HashMap<>();
            source.put("email", "must not be blank");
            BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid", source);

            // Act
            source.clear();

            // Assert
            assertEquals(1, ex.details().size());
        }
    }
}
