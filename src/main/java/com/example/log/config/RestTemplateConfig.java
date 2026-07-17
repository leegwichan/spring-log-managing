package com.example.log.config;

import com.example.log.interceptor.TracingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    private final TracingInterceptor tracingInterceptor;

    public RestTemplateConfig(TracingInterceptor tracingInterceptor) {
        this.tracingInterceptor = tracingInterceptor;
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Add tracing interceptor
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(tracingInterceptor);
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }
}
