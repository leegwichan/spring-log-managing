# Spring Boot 로그 관리 시스템 포트폴리오 프로젝트

## 프로젝트 개요

이 프로젝트는 Spring Boot 기반 애플리케이션에서 **관측 가능성(Observability)**을 확보하기 위한 로그 관리 및 모니터링 시스템입니다.

### 해결하려는 문제
- 수백 개의 동시 요청 속에서 특정 요청의 로그를 추적하기 어려움
- 느린 API를 사전에 감지하지 못해 장애 대응이 늦음
- 환경(개발/운영)별로 로그 설정을 수동으로 변경해야 함
- 로그 데이터를 분석하고 시각화할 도구가 없음

### 구현 목표
1. **요청별 로그 추적**: MDC를 활용한 traceId 자동 부여
2. **성능 모니터링**: 인터셉터로 API 응답 시간 측정
3. **환경별 설정 분리**: 멀티 프로파일로 dev/prod 로그 자동 전환
4. **실시간 대시보드**: 메트릭 수집 및 웹 UI 시각화

---

## 학습 로드맵

### Phase 1: Logback + 멀티프로파일 설정
**학습 문서**: `01_logback_multi_profile.md`

**핵심 개념**:
- Logback 기본 구조 (Appender, Encoder, Layout)
- 환경별 설정 분리 (`<springProfile>`)
- 로그 롤링 정책 (시간/크기 기반)
- JSON 구조화 로깅

**구현 내용**:
- `logback-spring.xml` 작성
- dev/prod 프로파일별 로그 레벨 분리
- 콘솔/파일 Appender 설정
- Logstash Encoder로 JSON 출력

---

### Phase 2: MDC 필터 구현
**학습 문서**: `02_mdc_filter.md`

**핵심 개념**:
- MDC (Mapped Diagnostic Context) 동작 원리
- ThreadLocal 기반 컨텍스트 전파
- Filter vs Interceptor 차이
- 메모리 누수 방지 (MDC.clear())

**구현 내용**:
- `MdcLoggingFilter` 작성
- traceId, requestUri, method 자동 저장
- 요청 시작/종료 시간 측정
- Logback 패턴에 MDC 값 포함

---

### Phase 3: 인터셉터로 성능 로깅
**학습 문서**: `03_interceptor_performance.md`

**핵심 개념**:
- HandlerInterceptor 생명주기
- preHandle / postHandle / afterCompletion
- 느린 API 감지 (Threshold 기반)
- 예외 발생 시 로깅 전략

**구현 내용**:
- `PerformanceLoggingInterceptor` 작성
- 컨트롤러 실행 시간 측정
- 3초 이상 소요 시 WARN 로그
- 응답 상태 코드별 분기 처리

---

### Phase 4: 모니터링 대시보드
**학습 문서**: `04_monitoring_dashboard.md`

**핵심 개념**:
- Spring Boot Actuator 엔드포인트
- Micrometer 메트릭 수집
- Prometheus 포맷 노출
- 커스텀 메트릭 생성

**구현 내용**:
- Actuator 설정 및 엔드포인트 활성화
- `ApiMetricsRecorder`로 메트릭 기록
- 로그 조회 REST API (`/api/logs`)
- Chart.js로 웹 대시보드 구현

---

## 기술 스택

### 필수 의존성
```gradle
// 기본 웹 프레임워크
implementation 'org.springframework.boot:spring-boot-starter-web'

// JSON 로깅
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'

// 모니터링
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### 선택 의존성
```gradle
// 웹 대시보드
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'

// 분산 추적 (MSA 환경)
implementation 'org.springframework.cloud:spring-cloud-starter-sleuth'
implementation 'org.springframework.cloud:spring-cloud-sleuth-zipkin'
```

---

## 프로젝트 구조

```
spring-log-managing/
├── src/main/java/com/example/log/
│   ├── config/
│   │   └── WebMvcConfig.java              # 인터셉터 등록
│   ├── filter/
│   │   └── MdcLoggingFilter.java          # MDC 설정 필터
│   ├── interceptor/
│   │   └── PerformanceLoggingInterceptor.java  # 성능 측정
│   ├── metrics/
│   │   └── ApiMetricsRecorder.java        # 메트릭 수집
│   ├── repository/
│   │   └── LogRepository.java             # 로그 저장소
│   ├── service/
│   │   └── MetricsService.java            # 통계 서비스
│   └── controller/
│       ├── LogApiController.java          # 로그 조회 API
│       └── MetricsApiController.java      # 메트릭 API
├── src/main/resources/
│   ├── logback-spring.xml                 # Logback 설정
│   ├── application.yml                    # 공통 설정
│   ├── application-dev.yml                # 개발 환경
│   ├── application-prod.yml               # 운영 환경
│   └── static/
│       └── dashboard.html                 # 웹 대시보드
└── docs/
    ├── 00_overview.md                     # 이 문서
    ├── 01_logback_multi_profile.md        # Phase 1 가이드
    ├── 02_mdc_filter.md                   # Phase 2 가이드
    ├── 03_interceptor_performance.md      # Phase 3 가이드
    └── 04_monitoring_dashboard.md         # Phase 4 가이드
```

---

## 학습 순서

### 1단계: 개념 학습 (각 Phase 문서 읽기)
- 각 Phase의 "핵심 개념" 섹션 숙지
- 왜 이 기술이 필요한지 이해
- Filter vs Interceptor 같은 비교 포인트 정리

### 2단계: 코드 작성
- 문서의 "구현할 코드" 섹션 따라하기
- 각 클래스의 역할과 상호작용 파악
- 주석 없이 깔끔하게 작성 (코드 자체가 설명)

### 3단계: 테스트
- "테스트 시나리오" 섹션 실행
- 로그 출력 형식 확인
- 대시보드에서 메트릭 조회

### 4단계: 포트폴리오 작성
- "포트폴리오 어필 포인트" 참고
- 기술 선택 이유와 트레이드오프 설명
- 실제 운영 환경에서 어떻게 활용될지 기술

---

## 데모 시나리오

### 1. 로컬 실행 (dev 프로파일)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```
→ 콘솔에 DEBUG 레벨 컬러 로그 출력

### 2. API 호출로 로그 생성
```bash
curl http://localhost:8080/api/users/1
curl http://localhost:8080/api/slow        # 4초 대기
curl http://localhost:8080/api/error       # 예외 발생
```

### 3. traceId로 로그 조회
```bash
curl http://localhost:8080/api/logs/trace/{traceId}
```
→ 해당 요청의 전체 로그 반환

### 4. 대시보드 확인
```
http://localhost:8080/dashboard.html
```
→ 전체 요청 수, 평균 응답시간, 느린 API Top 5 차트

### 5. Prometheus 메트릭 조회
```bash
curl http://localhost:8080/actuator/prometheus
```
→ Grafana 연동 가능

---

## 포트폴리오 작성 예시

### 프로젝트 요약
> Spring Boot 애플리케이션의 관측 가능성을 확보하기 위해 Logback, MDC, Micrometer를 활용한 로그 추적 및 실시간 모니터링 시스템을 구축했습니다. 요청별 고유 ID를 자동 부여하여 분산 환경에서도 로그를 추적할 수 있으며, API 응답 시간을 자동으로 측정하여 느린 API를 사전에 감지합니다.

### 기술적 도전과 해결
**문제**: 수백 개의 동시 요청 로그 속에서 특정 요청의 흐름 파악 불가  
**해결**: MDC로 요청별 traceId를 ThreadLocal에 저장하여 모든 로그에 자동 포함  
**결과**: 장애 발생 시 traceId 검색만으로 전체 요청 흐름 추적 가능

**문제**: 느린 API를 운영 중에 알아채기 어려움  
**해결**: HandlerInterceptor로 응답 시간을 측정하고 3초 이상 소요 시 WARN 로그 자동 출력  
**결과**: 성능 병목 구간을 사전에 파악하여 최적화 우선순위 결정

### 운영 고려사항
- 환경별 로그 레벨 자동 전환 (dev: DEBUG, prod: INFO)
- 로그 파일 자동 롤링 및 30일 보관 정책
- Prometheus 엔드포인트로 Grafana 연동 가능
- ELK Stack 연동 시 JSON 로그 바로 인덱싱

---

## 확장 가능성

### 1. Prometheus + Grafana 연동
- `/actuator/prometheus` 엔드포인트 활용
- 대시보드 템플릿: Spring Boot 2.1+ Statistics

### 2. ELK Stack 연동
- Filebeat로 로그 파일 수집
- Logstash에서 JSON 파싱 없이 바로 인덱싱
- Kibana로 시각화

### 3. 분산 추적 (MSA 환경)
- Spring Cloud Sleuth + Zipkin
- 서비스 간 traceId 전파 (HTTP Header)
- 서비스 호출 체인 시각화

### 4. 알림 시스템
- 느린 API 감지 시 Slack 알림
- 에러율 임계값 초과 시 PagerDuty 연동

---

## 참고 자료

### 공식 문서
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Logback Manual](https://logback.qos.ch/manual/index.html)
- [Micrometer Documentation](https://micrometer.io/docs)

### 추가 학습
- SLF4J MDC 동작 원리
- Prometheus 메트릭 타입 (Counter, Gauge, Histogram, Summary)
- OpenTelemetry 표준 (차세대 관측 가능성 프레임워크)

---

## 시작하기

1. **Phase 1 문서 읽기**: `docs/01_logback_multi_profile.md`
2. **의존성 추가**: `build.gradle` 수정
3. **Logback 설정**: `logback-spring.xml` 작성
4. **프로파일 설정**: `application-dev.yml`, `application-prod.yml` 생성
5. **테스트**: 로그 출력 확인

각 Phase를 순서대로 진행하며 점진적으로 시스템을 완성하세요!
