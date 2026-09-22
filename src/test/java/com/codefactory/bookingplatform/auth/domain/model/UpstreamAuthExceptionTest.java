package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Failure raised when the identity provider rejects or cannot serve a request. It carries the
 * classified {@link UpstreamAuthError} so the application layer can map it to an HTTP status
 * without parsing the upstream message.
 */
class UpstreamAuthExceptionTest {

    @Test
    @DisplayName("The two-argument constructor keeps the classified error and the message")
    void twoArgumentConstructorKeepsErrorAndMessage() {
        UpstreamAuthException ex = new UpstreamAuthException(UpstreamAuthError.INVALID_CREDENTIALS, "bad password");
        assertEquals(UpstreamAuthError.INVALID_CREDENTIALS, ex.error());
        assertEquals("bad password", ex.getMessage());
    }

    @Test
    @DisplayName("The two-argument constructor leaves the cause unset")
    void twoArgumentConstructorHasNoCause() {
        UpstreamAuthException ex = new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "provider down");
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("The three-argument constructor keeps the original failure as the cause, so the stack trace survives")
    void threeArgumentConstructorKeepsTheCause() {
        IOException cause = new IOException("connection reset");
        UpstreamAuthException ex = new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "provider down", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("The three-argument constructor keeps the classified error and the message as well")
    void threeArgumentConstructorKeepsErrorAndMessage() {
        UpstreamAuthException ex = new UpstreamAuthException(
                UpstreamAuthError.RATE_LIMITED, "too many calls", new IOException("429"));
        assertEquals(UpstreamAuthError.RATE_LIMITED, ex.error());
        assertEquals("too many calls", ex.getMessage());
    }

    @ParameterizedTest(name = "{0} is preserved by the exception")
    @EnumSource(UpstreamAuthError.class)
    @DisplayName("Any upstream failure can be carried unchanged by the exception")
    void anyErrorIsCarriedUnchanged(UpstreamAuthError error) {
        assertEquals(error, new UpstreamAuthException(error, "upstream said no").error());
    }

    @Test
    @DisplayName("The exception is unchecked so adapters do not have to declare it")
    void exceptionIsUnchecked() {
        assertInstanceOf(RuntimeException.class,
                new UpstreamAuthException(UpstreamAuthError.TOKEN_EXPIRED, "expired"));
    }

    @Test
    @DisplayName("The exception can be thrown and caught by its own type")
    void exceptionIsThrowable() {
        UpstreamAuthException thrown = assertThrows(UpstreamAuthException.class, () -> {
            throw new UpstreamAuthException(UpstreamAuthError.TOKEN_INVALID, "malformed token");
        });
        assertEquals(UpstreamAuthError.TOKEN_INVALID, thrown.error());
    }

    @Test
    @DisplayName("A null message is accepted and reported back as null rather than as an empty string")
    void nullMessageIsPreserved() {
        assertNull(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, null).getMessage());
    }
}
