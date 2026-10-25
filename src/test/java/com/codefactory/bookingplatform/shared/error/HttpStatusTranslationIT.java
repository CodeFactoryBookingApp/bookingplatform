package com.codefactory.bookingplatform.shared.error;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Protocol-level contract of the API (Lineamientos: "respuestas uniformes de error con códigos
 * HTTP correctos").
 *
 * <p>An unsupported method, media type or unknown path used to come back as 500 (D9; BUG-001,
 * BUG-002 and BUG-003 in Mantis). These run through the real dispatcher, which is where Spring MVC
 * raises those exceptions.
 */
class HttpStatusTranslationIT extends JwtIntegrationTestBase {

    @Test
    @DisplayName("Regression guard: malformed JSON still maps to 400 VALIDATION_ERROR")
    void malformedJsonStillMapsTo400() throws Exception {
        // Act
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andReturn().getResponse();

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", errorCodeOf(response));
    }

    @Test
    @DisplayName("D9: a GET on a POST-only endpoint answers 405 METHOD_NOT_ALLOWED with an Allow header")
    void unsupportedMethodIs405() throws Exception {
        // Act
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/registrations"))
                .andReturn().getResponse();

        // Assert
        assertEquals(405, response.getStatus());
        assertEquals("METHOD_NOT_ALLOWED", errorCodeOf(response));
        assertTrue(String.valueOf(response.getHeader("Allow")).contains("POST"), "Allow: " + response.getHeader("Allow"));
    }

    @Test
    @DisplayName("D9: a text/plain body answers 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaTypeIs415() throws Exception {
        // Act
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andReturn().getResponse();

        // Assert
        assertEquals(415, response.getStatus());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", errorCodeOf(response));
    }

    @Test
    @DisplayName("D9: an unknown public path answers 404 RESOURCE_NOT_FOUND")
    void unknownPathIs404() throws Exception {
        // Act
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/registrations/does-not-exist"))
                .andReturn().getResponse();

        // Assert
        assertEquals(404, response.getStatus());
        assertEquals("RESOURCE_NOT_FOUND", errorCodeOf(response));
    }

    private static String errorCodeOf(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        if (body == null || body.isBlank()) {
            return "<empty body>";
        }
        try {
            return com.jayway.jsonpath.JsonPath.read(body, "$.errorCode");
        } catch (RuntimeException ex) {
            return "<no errorCode in: " + body + ">";
        }
    }
}
