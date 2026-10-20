package com.codefactory.bookingplatform.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests for the trace id propagation filter. The test lives in the
 * filter package so that doFilterInternal can be driven directly, without a
 * servlet container.
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        MDC.clear();
        request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("An incoming X-Trace-Id is honoured and echoed back untouched")
    void incomingTraceIdIsHonoured() throws Exception {
        // Arrange
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-the-gateway");

        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        assertEquals("trace-from-the-gateway", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("The incoming trace id is the one visible in the MDC while the chain runs")
    void incomingTraceIdIsVisibleInMdcDuringTheChain() throws Exception {
        // Arrange
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-the-gateway");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        // Act
        filter.doFilterInternal(request, response, capturing);

        // Assert
        assertEquals("trace-from-the-gateway", seenInsideChain.get());
    }

    @ParameterizedTest(name = "a missing, empty or blank incoming header [{0}] is replaced by a generated id")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingOrBlankHeaderIsReplaced(String incoming) throws Exception {
        // Arrange
        if (incoming != null) {
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, incoming);
        }

        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        String generated = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    @Test
    @DisplayName("Two requests without an incoming header get two different generated ids")
    void generatedIdsAreUnique() throws Exception {
        // Arrange
        filter.doFilterInternal(request, response, chain);
        String first = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(secondRequest, secondResponse, chain);

        // Assert
        String second = secondResponse.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    @DisplayName("The response header and the MDC value seen by the chain are the same id")
    void headerAndMdcValueMatch() throws Exception {
        // Arrange
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        // Act
        filter.doFilterInternal(request, response, capturing);

        // Assert
        assertEquals(seenInsideChain.get(), response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("The chain is invoked exactly once with the very same request and response")
    void chainIsInvokedOnce() throws Exception {
        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("The MDC is cleaned once the chain returns normally")
    void mdcIsCleanedAfterASuccessfulChain() throws Exception {
        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("CONTEXT LEAK GUARD: the MDC is cleaned even when the chain throws an unchecked exception")
    void mdcIsCleanedWhenTheChainThrowsRuntimeException() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("unexpected")).when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        // Act
        Executable act = () -> filter.doFilterInternal(request, response, chain);

        // Assert
        assertThrows(IllegalStateException.class, act);
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("A failing request still answers with the trace id header, so the caller can report it")
    void headerIsSetBeforeTheChainRuns() throws Exception {
        // Arrange
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-before-failure");
        doThrow(new IllegalStateException("unexpected")).when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        // Act
        Executable act = () -> filter.doFilterInternal(request, response, chain);

        // Assert
        assertThrows(IllegalStateException.class, act);
        assertEquals("trace-before-failure", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }
}
