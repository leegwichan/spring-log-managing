package com.example.log.config;

import com.example.log.interceptor.PerformanceLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PerformanceLoggingInterceptor performanceLoggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(performanceLoggingInterceptor)
                .addPathPatterns("/api/**")  // /api/** 경로만 적용
                .excludePathPatterns("/api/health");  // 헬스체크는 제외
    }
}
