package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Esta excepción cruza la frontera del adaptador llevando el error ya clasificado, que es lo
 * que permite a la capa de aplicación decidir el estado HTTP sin interpretar el mensaje del
 * proveedor. Si al serializarse perdiera ese dato, la clasificación se perdería con él.
 */
class UpstreamAuthExceptionTest {

    @Test
    @DisplayName("A serialised upstream failure keeps its classified error and message")
    void survivesJavaSerialisation() throws Exception {
        UpstreamAuthException original =
                new UpstreamAuthException(UpstreamAuthError.RATE_LIMITED, "too many calls");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        UpstreamAuthException restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (UpstreamAuthException) in.readObject();
        }

        assertEquals(UpstreamAuthError.RATE_LIMITED, restored.error());
        assertEquals("too many calls", restored.getMessage());
    }
}
