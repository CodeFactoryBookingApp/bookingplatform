package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Carrier for a rejected business rule. It pairs an {@link ErrorCode} with a message and an
 * optional detail map that the exception handler publishes to the client, so the detail map must
 * be a defensive, unmodifiable copy that keeps the order in which the details were given.
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
    }
}
