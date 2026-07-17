package com.example.log.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Example service for calling external APIs with distributed tracing
 */
@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    private final RestTemplate restTemplate;

    public ExternalApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Call external API (tracing headers are automatically added by TracingInterceptor)
     */
    public String callExternalService(String url) {
        log.info("Calling external API: {}", url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            log.info("External API response received");
            return response;
        } catch (Exception e) {
            log.error("Failed to call external API: {}", e.getMessage(), e);
            throw e;
        }
    }
}
