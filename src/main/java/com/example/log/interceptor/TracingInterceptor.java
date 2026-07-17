package com.example.log.interceptor;

import com.example.log.config.ServiceInfoProperties;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RestTemplate Interceptor to propagate distributed tracing headers
 */
@Component
public class TracingInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    private static final String SERVICE_NAME_HEADER = "X-Service-Name";

    private final ServiceInfoProperties serviceInfo;

    public TracingInterceptor(ServiceInfoProperties serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        // Propagate traceId from MDC to downstream service
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            request.getHeaders().set(TRACE_ID_HEADER, traceId);
        }

        // Current spanId becomes parentSpanId for downstream service
        String spanId = MDC.get("spanId");
        if (spanId != null) {
            request.getHeaders().set(PARENT_SPAN_ID_HEADER, spanId);
        }

        // Add current service name
        request.getHeaders().set(SERVICE_NAME_HEADER, serviceInfo.getName());

        return execution.execute(request, body);
    }
}
