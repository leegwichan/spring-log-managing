package com.example.log.filter;

import com.example.log.config.ServiceInfoProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Component
@Order(1)
public class MdcLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    // HTTP Headers for distributed tracing
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";

    // MDC Keys
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String PARENT_SPAN_ID = "parentSpanId";
    private static final String SERVICE_NAME = "serviceName";
    private static final String INSTANCE_ID = "instanceId";
    private static final String VERSION = "version";
    private static final String ENVIRONMENT = "environment";
    private static final String REQUEST_URI = "requestUri";
    private static final String METHOD = "method";
    private static final String CLIENT_IP = "clientIp";
    private static final String HTTP_STATUS = "httpStatus";
    private static final String DURATION = "duration";

    private final ServiceInfoProperties serviceInfo;
    private final String instanceId;

    public MdcLoggingFilter(ServiceInfoProperties serviceInfo) {
        this.serviceInfo = serviceInfo;
        this.instanceId = generateInstanceId();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. Distributed Tracing IDs
            String traceId = getOrCreateTraceId(httpRequest);
            String spanId = generateSpanId();
            String parentSpanId = httpRequest.getHeader(PARENT_SPAN_ID_HEADER);

            MDC.put(TRACE_ID, traceId);
            MDC.put(SPAN_ID, spanId);
            if (parentSpanId != null && !parentSpanId.isEmpty()) {
                MDC.put(PARENT_SPAN_ID, parentSpanId);
            }

            // 2. Service Identity
            MDC.put(SERVICE_NAME, serviceInfo.getName());
            MDC.put(INSTANCE_ID, instanceId);
            MDC.put(VERSION, serviceInfo.getVersion());
            MDC.put(ENVIRONMENT, serviceInfo.getEnvironment());

            // 3. Request Context
            MDC.put(REQUEST_URI, httpRequest.getRequestURI());
            MDC.put(METHOD, httpRequest.getMethod());
            MDC.put(CLIENT_IP, getClientIp(httpRequest));

            // 4. Add tracing headers to response for downstream services
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            httpResponse.setHeader(SPAN_ID_HEADER, spanId);

            // 5. Process request
            long startTime = System.currentTimeMillis();

            chain.doFilter(request, response);

            // 6. Add performance metrics
            long duration = System.currentTimeMillis() - startTime;
            MDC.put(DURATION, String.valueOf(duration));
            MDC.put(HTTP_STATUS, String.valueOf(httpResponse.getStatus()));

            log.info("Request completed in {}ms with status {}", duration, httpResponse.getStatus());

        } finally {
            MDC.clear();
        }
    }

    /**
     * Get traceId from request header or create new one
     */
    private String getOrCreateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    /**
     * Generate unique spanId for current service span
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().substring(0, 16);
    }

    /**
     * Generate instance ID from hostname or container ID
     */
    private String generateInstanceId() {
        // Try to get hostname (Kubernetes Pod name or Docker container hostname)
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        // Try to get container ID from environment
        String containerId = System.getenv("CONTAINER_ID");
        if (containerId != null && !containerId.isEmpty()) {
            return containerId;
        }

        // Fallback to local hostname
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Extract client IP from request (considering proxy headers)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For may contain multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
