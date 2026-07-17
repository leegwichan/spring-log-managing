# Phase 2: MDC 필터 구현

## 1. 핵심 개념

### MDC (Mapped Diagnostic Context)란?
- SLF4J가 제공하는 **스레드별 컨텍스트 저장소**
- Key-Value 형태로 데이터를 저장하면, 해당 스레드의 모든 로그에 자동 포함
- 요청 시작부터 종료까지 동일한 값 유지

### 왜 MDC가 필요한가?

#### 문제 상황
```
[INFO] UserService - 사용자 조회 시작
[INFO] OrderService - 주문 조회 시작
[INFO] UserService - 사용자 조회 완료
[INFO] OrderService - 주문 조회 완료
```
→ 어떤 요청의 로그인지 구분 불가!

#### MDC 적용 후
```
[INFO] [traceId:abc123] UserService - 사용자 조회 시작
[INFO] [traceId:xyz789] OrderService - 주문 조회 시작
[INFO] [traceId:abc123] UserService - 사용자 조회 완료
[INFO] [traceId:xyz789] OrderService - 주문 조회 완료
```
→ traceId로 요청별 로그 추적 가능

---

## 2. MDC에 저장할 값

### 필수 정보
- **traceId**: 요청별 고유 ID (UUID)
- **requestUri**: 호출된 API 경로
- **method**: HTTP 메서드 (GET, POST 등)

### 선택 정보
- **userId**: 인증된 사용자 ID
- **sessionId**: 세션 ID
- **clientIp**: 클라이언트 IP 주소
- **userAgent**: 브라우저/클라이언트 정보

---

## 3. 구현할 코드

### 3.1 MDC 필터 클래스
```java
package com.example.log.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)  // 가장 먼저 실행되도록 설정
public class MdcLoggingFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String REQUEST_URI = "requestUri";
    private static final String METHOD = "method";
    private static final String CLIENT_IP = "clientIp";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            // 1. MDC에 값 저장
            MDC.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
            MDC.put(REQUEST_URI, httpRequest.getRequestURI());
            MDC.put(METHOD, httpRequest.getMethod());
            MDC.put(CLIENT_IP, getClientIp(httpRequest));
            
            // 2. 요청 처리 시작 로그
            long startTime = System.currentTimeMillis();
            
            // 3. 다음 필터/컨트롤러로 요청 전달
            chain.doFilter(request, response);
            
            // 4. 요청 처리 완료 로그
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed in {}ms", duration);
            
        } finally {
            // 5. MDC 정리 (메모리 누수 방지)
            MDC.clear();
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

### 3.2 Logback 패턴에 MDC 추가
**`logback-spring.xml` 수정**
```xml
<encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{traceId}] [%X{method} %X{requestUri}] %-5level %logger{36} - %msg%n</pattern>
</encoder>
```

`%X{key}` 형식으로 MDC 값을 패턴에 삽입

### 3.3 JSON 로깅에 MDC 포함
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeMdcKeyName>traceId</includeMdcKeyName>
    <includeMdcKeyName>requestUri</includeMdcKeyName>
    <includeMdcKeyName>method</includeMdcKeyName>
    <includeMdcKeyName>clientIp</includeMdcKeyName>
</encoder>
```

---

## 4. 테스트 시나리오

### 4.1 컨트롤러 작성
```java
@RestController
@RequestMapping("/api")
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/users/{id}")
    public String getUser(@PathVariable Long id) {
        log.info("UserController - 사용자 조회 시작");
        userService.findUser(id);
        log.info("UserController - 사용자 조회 완료");
        return "User: " + id;
    }
}
```

### 4.2 서비스 레이어
```java
@Service
@Slf4j
public class UserService {
    
    public void findUser(Long id) {
        log.info("UserService - DB 조회 중");
        // 실제 DB 로직...
        log.info("UserService - DB 조회 완료");
    }
}
```

### 4.3 예상 출력 결과
```
2026-07-12 15:30:01 [http-nio-8080-exec-1] [a3f8b921] [GET /api/users/1] INFO  c.e.l.controller.UserController - UserController - 사용자 조회 시작
2026-07-12 15:30:01 [http-nio-8080-exec-1] [a3f8b921] [GET /api/users/1] INFO  c.e.l.service.UserService - UserService - DB 조회 중
2026-07-12 15:30:01 [http-nio-8080-exec-1] [a3f8b921] [GET /api/users/1] INFO  c.e.l.service.UserService - UserService - DB 조회 완료
2026-07-12 15:30:01 [http-nio-8080-exec-1] [a3f8b921] [GET /api/users/1] INFO  c.e.l.controller.UserController - UserController - 사용자 조회 완료
2026-07-12 15:30:01 [http-nio-8080-exec-1] [a3f8b921] [GET /api/users/1] INFO  c.e.l.filter.MdcLoggingFilter - Request completed in 123ms
```

→ 모든 로그에 **동일한 traceId** 출력!

---

## 5. 주의사항

### ⚠️ 반드시 MDC.clear() 호출
```java
finally {
    MDC.clear();  // 톰캣 스레드 풀 재사용 시 이전 요청 정보가 남을 수 있음
}
```

### ⚠️ 비동기 처리 시 MDC 전파
```java
@Async
public void asyncTask() {
    // 새로운 스레드에서는 MDC 값이 사라짐!
    // TaskDecorator를 사용해 MDC 전파 필요
}
```

### ⚠️ 필터 순서 중요
```java
@Order(1)  // SecurityFilter보다 먼저 실행되어야 모든 로그에 traceId 포함
```

---

## 6. 포트폴리오 어필 포인트

### 문제 해결 능력
> "수백 건의 동시 요청 로그 속에서 특정 요청의 흐름을 추적하기 위해  
> MDC를 활용한 분산 추적 시스템을 구현했습니다."

### 운영 경험
- 로그 검색 시 `grep "traceId:abc123" application.log`로 특정 요청만 필터링
- 장애 발생 시 사용자가 받은 traceId로 전체 로그 추적
- 성능 병목 구간 파악 (요청 시작~종료 시간 측정)

### 확장 가능성
- 추후 Sleuth/Zipkin 연동 시 traceId 재사용 가능
- MSA 환경에서 서비스 간 traceId 전파 (HTTP Header)

---

## 7. 다음 단계
Phase 3에서는 **HandlerInterceptor**를 사용해  
컨트롤러 진입/응답 시점을 측정하고 느린 API를 감지하는 방법을 다룹니다.
