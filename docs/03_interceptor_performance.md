
# Phase 3: 인터셉터로 API 성능 로깅

## 1. 핵심 개념

### HandlerInterceptor란?
- Spring MVC가 제공하는 **컨트롤러 전후 처리 인터페이스**
- 컨트롤러 실행 전/후, 뷰 렌더링 후 시점에 로직 삽입 가능
- 필터보다 나중에 실행되며, 더 세밀한 제어 가능

### Filter vs Interceptor 차이

| 구분 | Filter | Interceptor |
|------|--------|-------------|
| 실행 시점 | Servlet 레벨 (DispatcherServlet 전) | Spring MVC 레벨 (Controller 전) |
| 접근 가능 정보 | Request, Response | Handler(Controller), ModelAndView |
| 용도 | 전역 설정 (MDC, 인증) | API별 성능 측정, 로깅 |

---

## 2. 구현 목표

### 측정할 메트릭
- **컨트롤러 실행 시간**: preHandle ~ postHandle
- **뷰 렌더링 시간**: postHandle ~ afterCompletion
- **전체 응답 시간**: preHandle ~ afterCompletion

### 느린 API 감지
- Threshold(예: 3초) 초과 시 WARN 로그
- 메서드명, 파라미터 포함

### 예외 발생 시 로깅
- Controller에서 예외 발생 시 상세 스택 트레이스 기록

---

## 3. 구현할 코드

### 3.1 성능 측정 인터셉터
```java
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
```

### 3.2 인터셉터 등록
```java
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
```

---

## 4. 고급 기능 추가

### 4.1 파라미터 로깅
```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod handlerMethod) {
        // 메서드 파라미터 정보 가져오기
        MethodParameter[] parameters = handlerMethod.getMethodParameters();
        log.info("요청 파라미터: {}", request.getParameterMap());
    }
    return true;
}
```

### 4.2 응답 상태 코드 로깅
```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                            Object handler, Exception ex) {
    int statusCode = response.getStatus();
    
    if (statusCode >= 400) {
        log.warn("❌ 에러 응답: {} ({})", statusCode, request.getRequestURI());
    } else {
        log.info("✅ 성공 응답: {} ({})", statusCode, request.getRequestURI());
    }
}
```

### 4.3 느린 API 메트릭 저장
```java
@Component
public class ApiMetricsCollector {
    private final ConcurrentHashMap<String, LongSummaryStatistics> metrics = new ConcurrentHashMap<>();
    
    public void record(String apiName, long duration) {
        metrics.computeIfAbsent(apiName, k -> new LongSummaryStatistics())
               .accept(duration);
    }
    
    public Map<String, LongSummaryStatistics> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }
}
```

---

## 5. 테스트 시나리오

### 5.1 정상 응답 API
```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userService.findById(id);
}
```
**예상 로그:**
```
[INFO] [a3f8b921] API 호출: UserController.getUser()
[INFO] [a3f8b921] API 응답 완료: UserController.getUser (123ms)
```

### 5.2 느린 API
```java
@GetMapping("/slow")
public String slowApi() throws InterruptedException {
    Thread.sleep(4000);  // 4초 대기
    return "slow";
}
```
**예상 로그:**
```
[INFO] [b7e2c634] API 호출: TestController.slowApi()
[WARN] [b7e2c634] ⚠️ 느린 API 감지: TestController.slowApi (4001ms)
```

### 5.3 예외 발생 API
```java
@GetMapping("/error")
public String errorApi() {
    throw new RuntimeException("의도적 예외");
}
```
**예상 로그:**
```
[INFO] [c9d3e745] API 호출: TestController.errorApi()
[ERROR] [c9d3e745] API 실행 중 예외 발생: TestController.errorApi
java.lang.RuntimeException: 의도적 예외
    at com.example.log.controller.TestController.errorApi(TestController.java:23)
    ...
```

---

## 6. 주의사항

### ⚠️ 인터셉터는 Controller에만 적용됨
- Filter는 모든 요청에 적용 (정적 리소스 포함)
- Interceptor는 DispatcherServlet을 거치는 요청만

### ⚠️ @Async 메서드는 측정 불가
```java
@Async
public void asyncMethod() {
    // Interceptor는 컨트롤러 메서드만 측정
    // 내부 비동기 메서드는 별도 측정 필요
}
```

### ⚠️ 예외 발생 시에도 afterCompletion 호출됨
```java
@Override
public void afterCompletion(..., Exception ex) {
    // ex != null이면 예외 발생한 경우
    // 정리 작업은 여기서 수행
}
```

---

## 7. 포트폴리오 어필 포인트

### 성능 모니터링 경험
> "API 응답 시간을 자동으로 측정하고, 3초 이상 소요되는 느린 API를 자동 감지하여  
> 성능 병목 지점을 사전에 파악하는 모니터링 시스템을 구축했습니다."

### 운영 이슈 대응
- 실제 운영 환경에서 특정 API가 느려지면 로그에 자동으로 WARN 표시
- 요청 파라미터까지 로깅되어 어떤 데이터에서 느려지는지 파악 가능

### 데이터 기반 최적화
- 수집된 메트릭을 바탕으로 "평균 응답시간 Top 10" 차트 생성
- 캐싱, 쿼리 최적화가 필요한 API 우선순위 결정

---

## 8. 다음 단계
Phase 4에서는 **Actuator + Micrometer**를 사용해  
실시간 메트릭을 수집하고 웹 대시보드에서 시각화하는 방법을 다룹니다.
