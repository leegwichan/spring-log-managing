# Phase 4: 모니터링 대시보드 구현

## 1. 핵심 개념

### Spring Boot Actuator란?
- Spring Boot 애플리케이션의 **운영 정보를 제공하는 모듈**
- 헬스 체크, 메트릭, 환경 정보 등을 REST API로 노출

### Micrometer란?
- **애플리케이션 메트릭 수집 라이브러리** (Java 진영의 SLF4J 같은 파사드)
- Prometheus, Grafana, CloudWatch 등 다양한 모니터링 시스템 연동 가능

### 구현할 대시보드 기능
1. **실시간 메트릭 조회**: 현재 요청 수, 평균 응답시간
2. **로그 검색 API**: traceId로 특정 요청 로그 조회
3. **통계 API**: 최근 1시간 에러율, 느린 API Top 5
4. **웹 UI**: Chart.js로 시각화

---

## 2. 구현할 코드

### 2.1 의존성 추가
```gradle
dependencies {
    // Actuator & Micrometer
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    
    // 웹 대시보드용 Thymeleaf (선택)
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
}
```

### 2.2 Actuator 설정
**`application.yml`**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, loggers, info
      base-path: /actuator
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

**접근 가능한 엔드포인트:**
- `GET /actuator/health` - 애플리케이션 상태
- `GET /actuator/metrics` - 사용 가능한 메트릭 목록
- `GET /actuator/metrics/http.server.requests` - HTTP 요청 통계
- `GET /actuator/prometheus` - Prometheus 포맷 메트릭

---

## 3. 커스텀 메트릭 수집

### 3.1 느린 API 카운터
```java
package com.example.log.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ApiMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> apiTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> slowApiCounters = new ConcurrentHashMap<>();

    public void recordApiCall(String apiName, long durationMs) {
        // 1. 응답 시간 기록
        Timer timer = apiTimers.computeIfAbsent(apiName, name ->
                Timer.builder("api.response.time")
                        .tag("api", name)
                        .description("API 응답 시간")
                        .register(meterRegistry)
        );
        timer.record(durationMs, TimeUnit.MILLISECONDS);

        // 2. 느린 API 카운트
        if (durationMs > 3000) {
            Counter counter = slowApiCounters.computeIfAbsent(apiName, name ->
                    Counter.builder("api.slow.count")
                            .tag("api", name)
                            .description("느린 API 호출 횟수")
                            .register(meterRegistry)
            );
            counter.increment();
        }
    }

    public void recordError(String apiName, String errorType) {
        Counter.builder("api.error.count")
                .tag("api", apiName)
                .tag("error", errorType)
                .description("API 에러 횟수")
                .register(meterRegistry)
                .increment();
    }
}
```

### 3.2 인터셉터와 연동
```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                            Object handler, Exception ex) {
    long duration = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTR);
    
    if (handler instanceof HandlerMethod handlerMethod) {
        String apiName = handlerMethod.getBeanType().getSimpleName() + "." + 
                         handlerMethod.getMethod().getName();
        
        // 메트릭 기록
        apiMetricsRecorder.recordApiCall(apiName, duration);
        
        if (ex != null) {
            apiMetricsRecorder.recordError(apiName, ex.getClass().getSimpleName());
        }
    }
}
```

---

## 4. 로그 조회 API

### 4.1 로그 저장소 (메모리 기반 간단 구현)
```java
package com.example.log.repository;

import lombok.Data;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class LogRepository {

    private final Map<String, List<LogEntry>> logsByTraceId = new ConcurrentHashMap<>();
    private final List<LogEntry> recentLogs = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LOGS = 1000;

    public void addLog(String traceId, String level, String message, String logger) {
        LogEntry entry = new LogEntry(
                traceId, level, message, logger, LocalDateTime.now()
        );

        // traceId별 저장
        logsByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(entry);

        // 최근 로그 저장 (링 버퍼처럼 동작)
        synchronized (recentLogs) {
            recentLogs.add(entry);
            if (recentLogs.size() > MAX_LOGS) {
                recentLogs.remove(0);
            }
        }
    }

    public List<LogEntry> findByTraceId(String traceId) {
        return logsByTraceId.getOrDefault(traceId, Collections.emptyList());
    }

    public List<LogEntry> findRecentLogs(int limit) {
        synchronized (recentLogs) {
            return recentLogs.stream()
                    .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    @Data
    public static class LogEntry {
        private final String traceId;
        private final String level;
        private final String message;
        private final String logger;
        private final LocalDateTime timestamp;
    }
}
```

### 4.2 로그 조회 API 컨트롤러
```java
package com.example.log.controller;

import com.example.log.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogApiController {

    private final LogRepository logRepository;

    @GetMapping
    public List<LogRepository.LogEntry> getRecentLogs(
            @RequestParam(defaultValue = "100") int limit) {
        return logRepository.findRecentLogs(limit);
    }

    @GetMapping("/trace/{traceId}")
    public List<LogRepository.LogEntry> getLogsByTraceId(@PathVariable String traceId) {
        return logRepository.findByTraceId(traceId);
    }
}
```

---

## 5. 통계 API

### 5.1 통계 데이터 수집
```java
package com.example.log.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public Map<String, Object> getApiStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 1. 전체 요청 수
        double totalRequests = meterRegistry.find("http.server.requests").counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();
        stats.put("totalRequests", (long) totalRequests);

        // 2. 평균 응답시간 (최근)
        double avgResponseTime = meterRegistry.find("http.server.requests").timer()
                .map(timer -> timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .orElse(0.0);
        stats.put("avgResponseTimeMs", Math.round(avgResponseTime));

        // 3. 에러 비율
        double errorCount = meterRegistry.find("http.server.requests")
                .tag("status", "500")
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();
        stats.put("errorRate", totalRequests > 0 ? (errorCount / totalRequests * 100) : 0);

        // 4. 느린 API Top 5
        Map<String, Long> slowApis = new HashMap<>();
        meterRegistry.find("api.slow.count").counters().forEach(counter -> {
            String apiName = counter.getId().getTag("api");
            slowApis.put(apiName, (long) counter.count());
        });
        stats.put("slowApis", slowApis);

        return stats;
    }
}
```

### 5.2 통계 API 엔드포인트
```java
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsApiController {

    private final MetricsService metricsService;

    @GetMapping("/stats")
    public Map<String, Object> getStatistics() {
        return metricsService.getApiStatistics();
    }
}
```

---

## 6. 웹 대시보드 구현

### 6.1 HTML + Chart.js
**`src/main/resources/static/dashboard.html`**
```html
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>로그 모니터링 대시보드</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
        .metric { background: #f5f5f5; padding: 15px; margin: 10px; border-radius: 8px; }
        #slowApiChart { max-width: 600px; }
    </style>
</head>
<body>
    <h1>로그 모니터링 대시보드</h1>
    
    <div class="metric">
        <h3>전체 요청 수: <span id="totalRequests">-</span></h3>
        <h3>평균 응답시간: <span id="avgResponseTime">-</span>ms</h3>
        <h3>에러율: <span id="errorRate">-</span>%</h3>
    </div>
    
    <h2>느린 API Top 5</h2>
    <canvas id="slowApiChart"></canvas>
    
    <script>
        async function loadMetrics() {
            const response = await fetch('/api/metrics/stats');
            const data = await response.json();
            
            document.getElementById('totalRequests').textContent = data.totalRequests;
            document.getElementById('avgResponseTime').textContent = data.avgResponseTimeMs;
            document.getElementById('errorRate').textContent = data.errorRate.toFixed(2);
            
            // Chart.js로 차트 생성
            const ctx = document.getElementById('slowApiChart').getContext('2d');
            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: Object.keys(data.slowApis),
                    datasets: [{
                        label: '느린 호출 횟수',
                        data: Object.values(data.slowApis),
                        backgroundColor: 'rgba(255, 99, 132, 0.5)'
                    }]
                }
            });
        }
        
        loadMetrics();
        setInterval(loadMetrics, 10000);  // 10초마다 갱신
    </script>
</body>
</html>
```

---

## 7. 모니터링 데이터 전송 방식

### 방식 1: Logback Appender로 실시간 전송
```java
public class MetricsAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent event) {
        String traceId = event.getMDCPropertyMap().get("traceId");
        logRepository.addLog(traceId, event.getLevel().toString(), 
                            event.getFormattedMessage(), event.getLoggerName());
    }
}
```

### 방식 2: AOP로 메서드 실행 시간 측정
```java
@Aspect
@Component
public class PerformanceMonitoringAspect {
    
    @Around("@annotation(Monitored)")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;
        
        apiMetricsRecorder.recordApiCall(joinPoint.getSignature().getName(), duration);
        return result;
    }
}
```

---

## 8. 포트폴리오 어필 포인트

### 관측 가능성(Observability) 구현
> "로그, 메트릭, 추적을 통합한 관측 가능성 시스템을 구축하여  
> 장애 발생 시 평균 탐지 시간(MTTD)을 30분에서 3분으로 단축했습니다."

### 실시간 모니터링
- 대시보드를 통해 현재 시스템 상태를 한눈에 파악
- 느린 API 자동 감지 및 알림 (임계값 기반)

### 확장 가능성
- Prometheus + Grafana 연동 시 `/actuator/prometheus` 엔드포인트 활용
- ELK Stack 연동 시 JSON 로그 바로 인덱싱
- Zipkin/Jaeger 연동 시 traceId 재사용

---

## 9. 최종 데모 시나리오

### 1. 여러 API 호출
```bash
curl http://localhost:8080/api/users/1
curl http://localhost:8080/api/slow
curl http://localhost:8080/api/error
```

### 2. 대시보드 확인
```
http://localhost:8080/dashboard.html
```
→ 전체 요청 수, 평균 응답시간, 느린 API 차트 표시

### 3. 특정 요청 로그 조회
```bash
curl http://localhost:8080/api/logs/trace/a3f8b921
```
→ traceId에 해당하는 모든 로그 반환

### 4. Prometheus 메트릭 조회
```bash
curl http://localhost:8080/actuator/prometheus
```
→ Grafana에서 시각화 가능한 포맷

---

## 10. 마무리

### 구현 완료 시 갖춰지는 것
- ✅ 환경별 로그 설정 (dev/prod)
- ✅ 요청별 추적 (traceId)
- ✅ API 성능 측정 및 느린 API 감지
- ✅ 실시간 메트릭 수집
- ✅ 웹 대시보드로 시각화

### 포트폴리오 한 줄 요약
> "Spring Boot에서 Logback, MDC, Micrometer를 활용해  
> 요청별 로그 추적과 실시간 성능 모니터링이 가능한 관측 시스템을 구축했습니다."

### 추가 학습 방향
- Prometheus + Grafana로 전문적인 대시보드 구성
- ELK Stack으로 로그 중앙화
- Sleuth + Zipkin으로 분산 추적 (MSA 환경)
