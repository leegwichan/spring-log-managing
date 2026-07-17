# Spring Log Managing

Spring Boot 기반의 구조화된 로깅 및 분산 추적 데모 애플리케이션입니다. MDC(Mapped Diagnostic Context)를 활용한 컨텍스트 전파와 ELK 스택 통합을 통해 프로덕션 수준의 로깅 아키텍처를 구현합니다.

## 주요 기능

- **분산 추적 (Distributed Tracing)**: 마이크로서비스 간 요청 추적을 위한 `traceId`, `spanId` 자동 생성 및 전파
- **구조화된 로깅**: JSON 포맷 로그를 통한 효율적인 로그 검색 및 분석
- **MDC 컨텍스트 관리**: 요청별 메타데이터 자동 수집 (서비스 정보, 요청 정보, 성능 메트릭)
- **ELK 스택 통합**: Elasticsearch, Logstash, Kibana를 활용한 중앙화된 로그 관리
- **환경별 설정**: dev/prod 프로파일에 따른 로깅 전략 분리
- **비동기 로깅**: AsyncAppender를 통한 논블로킹 로그 처리

## 기술 스택

- **Java 21**
- **Spring Boot 4.1.0**
- **Gradle 9.5.1**
- **Logback + Logstash Encoder**: JSON 구조화 로깅
- **Lombok**: 보일러플레이트 코드 감소
- **Docker Compose**: ELK 스택 로컬 환경

## 시작하기

### 사전 요구사항

- Java 21 이상
- Docker & Docker Compose (ELK 스택 사용 시)
- 최소 4GB 메모리 (Elasticsearch 실행 시)

### 로컬 실행 (콘솔 로그만)

```bash
# 프로젝트 빌드
./gradlew build

# 로컬 프로파일로 실행
./gradlew bootRun

# 또는 특정 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

애플리케이션이 http://localhost:8080 에서 실행됩니다.

### Docker로 전체 스택 실행 (ELK + Spring Boot)

```bash
# ELK 스택 + Spring Boot Dev/Prod 환경 모두 실행
cd docker
docker-compose up -d

# 로그 확인
docker-compose logs -f spring-app-dev
```

**접속 정보:**
- Spring Boot (Dev): http://localhost:8080
- Spring Boot (Prod): http://localhost:8081
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200

상세한 Docker 사용법은 [docker/README.md](docker/README.md)를 참고하세요.

## API 예제

### 테스트 엔드포인트

```bash
# 기본 테스트
curl http://localhost:8080/api/test/hello

# 사용자 조회 (외부 API 호출 시뮬레이션)
curl http://localhost:8080/api/users/1

# 에러 발생 테스트
curl http://localhost:8080/api/test/error
```

### 분산 추적 테스트

```bash
# 커스텀 traceId로 요청
curl -H "X-Trace-Id: my-custom-trace-123" \
     http://localhost:8080/api/test/hello

# 응답 헤더에서 traceId, spanId 확인
curl -i http://localhost:8080/api/test/hello
```

## 로그 구조

각 요청은 다음과 같은 MDC 컨텍스트 정보를 포함합니다:

```json
{
  "@timestamp": "2026-07-17T10:00:00.123Z",
  "message": "Request completed in 45ms with status 200",
  "level": "INFO",
  "logger_name": "com.example.log.filter.MdcLoggingFilter",
  
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "spanId": "x1y2z3w4v5u6t7s8",
  "serviceName": "spring-log-managing",
  "instanceId": "spring-app-dev-container-id",
  "version": "1.0.0",
  "environment": "dev",
  
  "requestUri": "/api/users/1",
  "method": "GET",
  "clientIp": "172.18.0.1",
  "httpStatus": "200",
  "duration": "45"
}
```

## Kibana에서 로그 확인

1. Kibana 접속: http://localhost:5601
2. Index Pattern 생성: `spring-logs-*` 또는 `spring-logs-dev-*`
3. Discover 메뉴에서 로그 검색

**유용한 검색 쿼리:**

```
# 특정 traceId로 전체 요청 추적
traceId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

# 에러 로그만 필터링
level: ERROR

# 느린 요청 찾기 (100ms 이상)
duration > 100

# 특정 API 엔드포인트 로그
requestUri: "/api/users/*"

# 환경별 필터링
app_environment: "prod"
```

## 프로젝트 구조

```
.
├── src/main/java/com/example/log/
│   ├── SpringLogManagingApplication.java   # 메인 애플리케이션
│   ├── config/                             # 설정 클래스
│   │   ├── ServiceInfoProperties.java      # 서비스 메타데이터 바인딩
│   │   ├── WebMvcConfig.java               # 인터셉터 등록
│   │   └── RestTemplateConfig.java         # HTTP 클라이언트 설정
│   ├── filter/
│   │   └── MdcLoggingFilter.java           # MDC 컨텍스트 초기화 필터
│   ├── interceptor/                        # 요청/응답 인터셉터
│   │   ├── PerformanceLoggingInterceptor.java
│   │   └── TracingInterceptor.java         # 분산 추적 헤더 전파
│   ├── controller/                         # REST 컨트롤러
│   ├── service/                            # 비즈니스 로직
│   └── exception/
│       └── GlobalExceptionHandler.java     # 전역 예외 처리
├── src/main/resources/
│   ├── application.properties              # 기본 설정
│   ├── application-dev.yml                 # dev 프로파일 설정
│   ├── application-prod.yml                # prod 프로파일 설정
│   └── logback-spring.xml                  # Logback 설정 (프로파일별)
├── docker/
│   ├── docker-compose.yml                  # ELK + Spring Boot 전체 구성
│   ├── Dockerfile                          # Spring Boot 이미지
│   ├── logstash/pipeline/                  # Logstash 파이프라인 설정
│   └── README.md                           # Docker 환경 상세 가이드
└── CLAUDE.md                               # 개발자 가이드
```

## 아키텍처 흐름

```
1. HTTP 요청 수신
   ↓
2. MdcLoggingFilter (@Order(1))
   - traceId/spanId 생성 또는 헤더에서 추출
   - MDC에 컨텍스트 정보 저장
   - 응답 헤더에 tracing ID 추가
   ↓
3. PerformanceLoggingInterceptor
   - 요청 처리 시간 측정
   ↓
4. Controller → Service
   - 비즈니스 로직 실행
   - 모든 로그는 자동으로 MDC 컨텍스트 포함
   ↓
5. GlobalExceptionHandler (에러 발생 시)
   - 예외를 로깅 (traceId와 함께)
   ↓
6. 응답 반환 후 MDC.clear()
   ↓
7. Logback → Logstash (TCP) → Elasticsearch
   ↓
8. Kibana에서 시각화
```

## 환경별 설정

### Local (기본)
- 프로파일: 미지정
- 로그 출력: 콘솔만
- Logstash 연결: 없음

### Dev
- 프로파일: `dev`
- 로그 출력: 콘솔 + Logstash
- Elasticsearch 인덱스: `spring-logs-dev-YYYY.MM.dd`
- 로그 레벨: INFO

### Prod
- 프로파일: `prod`
- 로그 출력: 콘솔 + Logstash
- Elasticsearch 인덱스: `spring-logs-prod-YYYY.MM.dd`
- 로그 레벨: INFO
- AsyncAppender `neverBlock: false` (로그 유실 방지)

## 커스터마이징

### 로그 레벨 변경

`application-dev.yml` 또는 `application-prod.yml`에 추가:

```yaml
logging:
  level:
    com.example.log: DEBUG
    org.springframework.web: WARN
```

### 새로운 MDC 필드 추가

`MdcLoggingFilter.java`에서 MDC 필드 추가:

```java
MDC.put("userId", extractUserId(request));
```

`logback-spring.xml`에 필드 포함 설정:

```xml
<includeMdcKeyName>userId</includeMdcKeyName>
```

### Logstash 연결 설정

환경 변수 또는 application.yml에서 설정:

```bash
export LOGSTASH_HOST=my-logstash-server
export LOGSTASH_PORT=5000
```

또는

```yaml
logstash:
  host: my-logstash-server
  port: 5000
```

## 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests com.example.log.SpringLogManagingApplicationTests
```

## 트러블슈팅

### Logstash 연결 실패

```bash
# Logstash 상태 확인
curl http://localhost:9600/_node/stats/pipelines

# 컨테이너 로그 확인
docker-compose logs logstash
```

### Elasticsearch 메모리 부족

`docker/docker-compose.yml`에서 힙 크기 조정:

```yaml
environment:
  - "ES_JAVA_OPTS=-Xms1g -Xmx1g"  # 기본: 512m
```

### 로그가 Kibana에 보이지 않음

```bash
# Elasticsearch 인덱스 확인
curl http://localhost:9200/_cat/indices?v

# 데이터가 있는지 확인
curl http://localhost:9200/spring-logs-dev-*/_search?size=1
```

## 참고 자료

- [Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Logstash Logback Encoder](https://github.com/logfellow/logstash-logback-encoder)
- [Elastic Stack Documentation](https://www.elastic.co/guide/index.html)
- [MDC (Mapped Diagnostic Context)](https://logback.qos.ch/manual/mdc.html)

## 라이센스

이 프로젝트는 교육 및 데모 목적으로 제공됩니다.
