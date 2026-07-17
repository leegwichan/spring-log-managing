package com.example.log.filter;

import com.example.log.config.ServiceInfoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;
    private ServiceInfoProperties serviceInfo;

    @BeforeEach
    void setUp() {
        serviceInfo = new ServiceInfoProperties();
        serviceInfo.setName("test-service");
        serviceInfo.setVersion("1.0.0-test");
        serviceInfo.setEnvironment("test");

        filter = new MdcLoggingFilter(serviceInfo);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testMdcValuesAreSetDuringRequest() throws ServletException, IOException {
        // Given
        request.setMethod("GET");
        request.setRequestURI("/api/users/1");
        request.setRemoteAddr("127.0.0.1");

        // When
        doAnswer(invocation -> {
            // MDC 값이 설정되었는지 확인
            assertNotNull(MDC.get("traceId"));
            assertNotNull(MDC.get("spanId"));
            assertEquals("GET", MDC.get("method"));
            assertEquals("/api/users/1", MDC.get("requestUri"));
            assertEquals("127.0.0.1", MDC.get("clientIp"));
            assertEquals("test-service", MDC.get("serviceName"));
            assertNotNull(MDC.get("instanceId"));
            assertEquals("1.0.0-test", MDC.get("version"));
            assertEquals("test", MDC.get("environment"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testMdcIsClearedAfterRequest() throws ServletException, IOException {
        // Given
        request.setMethod("POST");
        request.setRequestURI("/api/users");

        // When
        filter.doFilter(request, response, filterChain);

        // Then - MDC should be cleared after request
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
        assertNull(MDC.get("method"));
        assertNull(MDC.get("requestUri"));
        assertNull(MDC.get("clientIp"));
        assertNull(MDC.get("serviceName"));
    }

    @Test
    void testClientIpFromXForwardedForHeader() throws ServletException, IOException {
        // Given
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.setRemoteAddr("127.0.0.1");

        // When
        doAnswer(invocation -> {
            assertEquals("203.0.113.1", MDC.get("clientIp"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testTraceIdIsUnique() throws ServletException, IOException {
        // Given
        request.setMethod("GET");
        request.setRequestURI("/api/users/1");

        String[] traceIds = new String[2];

        // When - 첫 번째 요청
        doAnswer(invocation -> {
            traceIds[0] = MDC.get("traceId");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // When - 두 번째 요청
        doAnswer(invocation -> {
            traceIds[1] = MDC.get("traceId");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // Then
        assertNotNull(traceIds[0]);
        assertNotNull(traceIds[1]);
        assertNotEquals(traceIds[0], traceIds[1]);
    }

    @Test
    void testTraceIdPropagationFromUpstream() throws ServletException, IOException {
        // Given - upstream service sends traceId
        String upstreamTraceId = "upstream-trace-123";
        request.setMethod("GET");
        request.setRequestURI("/api/users/1");
        request.addHeader("X-Trace-Id", upstreamTraceId);

        // When
        doAnswer(invocation -> {
            // Should use upstream traceId, not create new one
            assertEquals(upstreamTraceId, MDC.get("traceId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testParentSpanIdPropagation() throws ServletException, IOException {
        // Given - upstream service sends spanId which becomes our parentSpanId
        String upstreamSpanId = "upstream-span-456";
        request.setMethod("GET");
        request.setRequestURI("/api/users/1");
        request.addHeader("X-Parent-Span-Id", upstreamSpanId);

        // When
        doAnswer(invocation -> {
            // Should receive upstream spanId as parentSpanId
            assertEquals(upstreamSpanId, MDC.get("parentSpanId"));
            // Should create new spanId for current service
            assertNotNull(MDC.get("spanId"));
            assertNotEquals(upstreamSpanId, MDC.get("spanId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
