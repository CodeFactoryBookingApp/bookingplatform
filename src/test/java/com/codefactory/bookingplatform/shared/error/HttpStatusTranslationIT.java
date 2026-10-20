package com.codefactory.bookingplatform.shared.error;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Protocol-level contract of the API (Lineamientos: "respuestas uniformes de error con códigos
 * HTTP correctos").
 *
 * <p>The wrong translations of an unsupported method, media type or unknown path (500 instead of
 * 405/415/404) are reported as BUG-001, BUG-002 and BUG-003; they are not pinned here, because a
 * green test asserting the defect would read as the expected behaviour.
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
