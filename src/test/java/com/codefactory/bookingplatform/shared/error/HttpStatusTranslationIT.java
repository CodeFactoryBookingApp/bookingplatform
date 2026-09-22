package com.codefactory.bookingplatform.shared.error;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Protocol-level contract of the API (Lineamientos: "respuestas uniformes de error con códigos
 * HTTP correctos").
 *
 * <p>{@code GlobalExceptionHandler} is annotated {@code @Order(HIGHEST_PRECEDENCE)} and declares
 * {@code @ExceptionHandler(Exception.class)}. Spring resolves handler methods by walking the
 * advices in order, so that catch-all sits in front of Spring MVC's own
 * {@code DefaultHandlerExceptionResolver} / {@code ProblemDetailsExceptionHandler}, which are the
 * things that turn {@code HttpRequestMethodNotSupportedException} into 405,
 * {@code HttpMediaTypeNotSupportedException} into 415 and {@code NoResourceFoundException} into 404.
 *
 * <p>Every request below targets a {@code permitAll} path, so security is out of the picture and
 * what we measure is purely the MVC translation.
 */
class HttpStatusTranslationIT extends JwtIntegrationTestBase {

    @Test
    @DisplayName("DEFECT D9: an unsupported HTTP method returns 500 instead of 405")
    void unsupportedMethodShouldBe405() throws Exception {
        // /api/v1/registrations only maps POST
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/registrations"))
                .andReturn().getResponse();

        assertEquals(500, response.getStatus(), """
                Current behaviour pinned. RFC 9110 and the API guideline require 405 Method Not Allowed \
                with an Allow header. If this now returns 405 the defect was fixed: flip the expectation.""");
        assertEquals("INTERNAL_ERROR", errorCodeOf(response));
        assertEquals("", response.getHeader("Allow") == null ? "" : response.getHeader("Allow"),
                "a 405 must carry Allow; today no Allow header is emitted at all");
    }

    @Test
    @DisplayName("DEFECT D9: an unsupported media type returns 500 instead of 415")
    void unsupportedMediaTypeShouldBe415() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("fullName=Ana"))
                .andReturn().getResponse();

        assertEquals(500, response.getStatus(), """
                Current behaviour pinned. The guideline requires 415 Unsupported Media Type. \
                If this now returns 415 the defect was fixed: flip the expectation.""");
        assertEquals("INTERNAL_ERROR", errorCodeOf(response));
    }

    @Test
    @DisplayName("DEFECT D9: an unknown path under a public prefix returns 500 instead of 404")
    void unknownPathShouldBe404() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/registrations/no-such-resource"))
                .andReturn().getResponse();

        assertEquals(500, response.getStatus(), """
                Current behaviour pinned. An unmapped path must answer 404 Not Found. \
                If this now returns 404 the defect was fixed: flip the expectation.""");
        assertEquals("INTERNAL_ERROR", errorCodeOf(response));
    }

    @Test
    @DisplayName("DEFECT D9: an unsupported method on an authenticated endpoint also returns 500")
    void unsupportedMethodOnProtectedEndpointShouldBe405() throws Exception {
        // /api/v1/auth/me maps GET only; a valid token gets us past security so MVC is what answers
        String token = jwks().accessToken(java.util.UUID.randomUUID(), "ana.perez@example.com", "CLIENT");

        MockHttpServletResponse response = mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse();

        assertEquals(500, response.getStatus(),
                "Current behaviour pinned; 405 is the correct answer. Flip the expectation once fixed.");
    }

    @Test
    @DisplayName("Regression guard: malformed JSON still maps to 400 VALIDATION_ERROR")
    void malformedJsonStillMapsTo400() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andReturn().getResponse();

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
