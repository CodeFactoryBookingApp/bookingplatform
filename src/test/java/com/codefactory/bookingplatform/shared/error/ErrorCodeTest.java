package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El catálogo de errores es contrato público: cada código viaja al cliente dentro del
 * ProblemDetail y lleva asociado un estado HTTP del que dependen los consumidores de la API.
 *
 * <p>Se prueba como una tabla de decisión, que es lo que es. Cambiar el estado de un código
 * rompe a quien lo consume, así que la tabla está aquí para que ese cambio no pase inadvertido.</p>
 */
class ErrorCodeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "VALIDATION_ERROR,            BAD_REQUEST",
            "MINOR_NOT_ALLOWED,           BAD_REQUEST",
            "PASSWORD_TOO_WEAK,           BAD_REQUEST",
            "VERIFICATION_TOKEN_INVALID,  BAD_REQUEST",
            "AUTH_REQUIRED,               UNAUTHORIZED",
            "INVALID_CREDENTIALS,         UNAUTHORIZED",
            "AUTH_TOKEN_INVALID,          UNAUTHORIZED",
            "RESOURCE_NOT_FOUND,          NOT_FOUND",
            "EMAIL_NOT_CONFIRMED,         FORBIDDEN",
            "ACCESS_DENIED,               FORBIDDEN",
            "DUPLICATE_EMAIL,             CONFLICT",
            "DUPLICATE_DOCUMENT,          CONFLICT",
            "RATE_LIMITED,                TOO_MANY_REQUESTS",
            "ACCOUNT_LOCKED,              TOO_MANY_REQUESTS",
            "UPSTREAM_AUTH_ERROR,         BAD_GATEWAY",
            "INTERNAL_ERROR,              INTERNAL_SERVER_ERROR"
    })
    @DisplayName("Each error code maps to the HTTP status its semantics demand")
    void statusMatchesTheSemanticsOfTheCode(ErrorCode code, HttpStatus expectedStatus) {
        assertEquals(expectedStatus, code.status());
    }

    @Test
    @DisplayName("The table above covers the whole catalogue, so a new code cannot slip through untested")
    void decisionTableCoversTheWholeCatalogue() {
        assertEquals(16, ErrorCode.values().length,
                "A code was added or removed: update the status decision table in this test");
    }
}
