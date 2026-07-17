package com.example.log.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Component
public class PerformanceLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";
    private static final long SLOW_API_THRESHOLD_MS = 3000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 시작 시간 기록
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        // 2. 요청 정보 로깅
        if (handler instanceof HandlerMethod handlerMethod) {
            String className = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            log.info("API 호출: {}.{}()", className, methodName);
        }

        return true;  // true: 다음 인터셉터/컨트롤러 실행, false: 중단
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // 컨트롤러 실행 완료 시점
        long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = System.currentTimeMillis() - startTime;

        log.debug("컨트롤러 실행 시간: {}ms", duration);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 뷰 렌더링 완료 시점 (또는 예외 발생)
        long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long totalDuration = System.currentTimeMillis() - startTime;

        if (handler instanceof HandlerMethod handlerMethod) {
            String apiName = handlerMethod.getBeanType().getSimpleName() + "." +
                             handlerMethod.getMethod().getName();

            // 느린 API 감지
            if (totalDuration > SLOW_API_THRESHOLD_MS) {
                log.warn("⚠️ 느린 API 감지: {} ({}ms)", apiName, totalDuration);
            } else {
                log.info("API 응답 완료: {} ({}ms)", apiName, totalDuration);
            }

            // 예외 발생 시
            if (ex != null) {
                log.error("API 실행 중 예외 발생: {}", apiName, ex);
            }
        }
    }
}
