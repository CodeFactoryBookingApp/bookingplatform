package com.codefactory.bookingplatform.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-the-gateway");

        filter.doFilterInternal(request, response, chain);

        assertEquals("trace-from-the-gateway", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("The incoming trace id is the one visible in the MDC while the chain runs")
    void incomingTraceIdIsVisibleInMdcDuringTheChain() throws Exception {
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-the-gateway");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilterInternal(request, response, capturing);

        assertEquals("trace-from-the-gateway", seenInsideChain.get());
    }

    @ParameterizedTest(name = "a missing, empty or blank incoming header [{0}] is replaced by a generated id")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    void missingOrBlankHeaderIsReplaced(String incoming) throws Exception {
        if (incoming != null) {
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, incoming);
        }

        filter.doFilterInternal(request, response, chain);

        String generated = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    @Test
    @DisplayName("The response always carries the trace id header, even when none came in")
    void responseAlwaysCarriesTheHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertNotNull(response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("Two requests without an incoming header get two different generated ids")
    void generatedIdsAreUnique() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String first = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/v1/auth/me"), secondResponse, chain);
        String second = secondResponse.getHeader(TraceIdFilter.TRACE_ID_HEADER);

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    @DisplayName("The response header and the MDC value seen by the chain are the same id")
    void headerAndMdcValueMatch() throws Exception {
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilterInternal(request, response, capturing);

        assertEquals(seenInsideChain.get(), response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("The chain is invoked exactly once with the very same request and response")
    void chainIsInvokedOnce() throws Exception {
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("The MDC is cleaned once the chain returns normally")
    void mdcIsCleanedAfterASuccessfulChain() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("CONTEXT LEAK GUARD: the MDC is cleaned even when the chain throws a ServletException")
    void mdcIsCleanedWhenTheChainThrowsServletException() throws Exception {
        doThrow(new ServletException("boom")).when(chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("CONTEXT LEAK GUARD: the MDC is cleaned even when the chain throws an IOException")
    void mdcIsCleanedWhenTheChainThrowsIoException() throws Exception {
        doThrow(new IOException("socket closed")).when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(IOException.class, () -> filter.doFilterInternal(request, response, chain));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("CONTEXT LEAK GUARD: the MDC is cleaned even when the chain throws an unchecked exception")
    void mdcIsCleanedWhenTheChainThrowsRuntimeException() throws Exception {
        doThrow(new IllegalStateException("unexpected")).when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(IllegalStateException.class, () -> filter.doFilterInternal(request, response, chain));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("A failing request still answers with the trace id header, so the caller can report it")
    void headerIsSetBeforeTheChainRuns() throws Exception {
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-before-failure");
        doThrow(new IllegalStateException("unexpected")).when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(IllegalStateException.class, () -> filter.doFilterInternal(request, response, chain));
        assertEquals("trace-before-failure", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("A stale trace id left over from a previous request is overwritten, never reused")
    void staleMdcValueIsOverwritten() throws Exception {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "stale-value-from-a-previous-request");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "fresh-value");

        filter.doFilterInternal(request, response, capturing);

        assertEquals("fresh-value", seenInsideChain.get());
    }

    @Test
    @DisplayName("Going through the public doFilter entry point behaves like doFilterInternal")
    void publicDoFilterEntryPointWorks() throws Exception {
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "through-the-front-door");

        filter.doFilter(request, response, chain);

        assertEquals("through-the-front-door", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Only one trace id header value is written, never a second appended one")
    void headerIsSetNotAdded() throws Exception {
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "single-value");

        filter.doFilterInternal(request, response, chain);

        assertEquals(1, response.getHeaders(TraceIdFilter.TRACE_ID_HEADER).size());
    }

    @Test
    @DisplayName("The published constants keep the contract the logging pattern relies on")
    void constantsAreStable() {
        assertEquals("X-Trace-Id", TraceIdFilter.TRACE_ID_HEADER);
        assertEquals("traceId", TraceIdFilter.TRACE_ID_MDC_KEY);
    }

    @Test
    @DisplayName("A generated trace id is a random UUID, not a predictable counter")
    void generatedTraceIdIsAUuid() throws Exception {
        filter.doFilterInternal(request, response, chain);

        String generated = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(generated);
        assertTrue(generated.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
}
