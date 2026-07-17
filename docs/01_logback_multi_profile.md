# Phase 1: Logback + 멀티프로파일 설정

## 1. 핵심 개념

### Logback이란?
- SLF4J의 구현체로, Spring Boot의 기본 로깅 프레임워크
- Log4j보다 빠르고 메모리 효율적
- 런타임 중 설정 변경 가능

### 왜 Logback인가?
- **구조화된 로깅**: JSON 형식으로 출력 가능 (ELK 스택 연동 용이)
- **환경별 분리**: 개발/운영 환경에서 다른 로그 레벨/포맷 사용
- **파일 관리**: 자동 롤링, 압축, 오래된 로그 삭제

### 멀티 프로파일 전략
```
application.yml           # 공통 설정
application-dev.yml       # 로컬 개발용 (DEBUG 레벨, 콘솔 출력)
application-staging.yml   # 스테이징 (INFO 레벨, 파일 + 콘솔)
application-prod.yml      # 운영 (WARN 레벨, 파일만, JSON 형식)
```

---

## 2. 구현할 코드

### 2.1 의존성 추가 (`build.gradle`)
```gradle
dependencies {
    // Logback은 spring-boot-starter-web에 포함되어 있음
    
    // JSON 형식 로깅을 위한 라이브러리
    implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
    
    // 추가 유틸리티
    implementation 'ch.qos.logback:logback-classic'
}
```

### 2.2 Logback 설정 파일 (`src/main/resources/logback-spring.xml`)
```xml
<configuration>
    <!-- 환경별 분기 -->
    <springProfile name="dev">
        <!-- 콘솔 출력, 컬러 로그 -->
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <!-- 파일 출력 + JSON 형식 -->
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/application.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
                <maxHistory>30</maxHistory>
            </rollingPolicy>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO">
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

### 2.3 프로파일별 설정 파일

**`application-dev.yml`**
```yaml
spring:
  application:
    name: spring-log-managing

logging:
  level:
    com.example.log: DEBUG
    org.springframework.web: DEBUG
```

**`application-prod.yml`**
```yaml
spring:
  application:
    name: spring-log-managing

logging:
  level:
    com.example.log: INFO
    org.springframework.web: WARN
  file:
    path: /var/log/spring-app
```

---

## 3. 테스트 코드

### 간단한 컨트롤러로 로그 확인
```java
@RestController
@Slf4j
public class LogTestController {
    
    @GetMapping("/test-log")
    public String testLog() {
        log.trace("TRACE 레벨 로그");
        log.debug("DEBUG 레벨 로그");
        log.info("INFO 레벨 로그");
        log.warn("WARN 레벨 로그");
        log.error("ERROR 레벨 로그");
        return "로그 테스트 완료";
    }
}
```

### 실행 방법
```bash
# dev 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# prod 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## 4. 확인해야 할 포인트

### ✅ dev 환경
- 콘솔에 컬러 로그 출력
- DEBUG 레벨까지 보임
- 타임스탬프 형식 확인

### ✅ prod 환경
- `logs/application.log` 파일 생성 확인
- JSON 형식으로 출력되는지 확인
- INFO 레벨 이상만 기록되는지 확인
- 날짜별 롤링 파일 생성 (하루 뒤 확인)

---

## 5. 포트폴리오 어필 포인트

### 기술적 결정 이유
> "로컬 개발 시 디버깅 효율성을 위해 콘솔에 상세 로그를 출력하고,  
> 운영 환경에서는 디스크 I/O를 줄이기 위해 INFO 레벨 이상만 JSON 형식으로 파일에 기록했습니다."

### 운영 고려사항
- 로그 파일 자동 압축 및 30일 보관 정책
- 프로파일 전환만으로 환경 설정 변경 (재배포 불필요)
- JSON 로그는 ELK 스택 연동 시 파싱 없이 바로 인덱싱 가능

---

## 6. 다음 단계
Phase 2에서는 **MDC(Mapped Diagnostic Context)**를 활용해  
각 로그에 요청별 추적 ID를 자동으로 삽입하는 방법을 다룹니다.
