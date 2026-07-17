# Docker ELK Stack + Spring Boot 구성

이 디렉토리는 Elasticsearch, Logstash, Kibana를 Docker로 구성하고 Spring Boot 애플리케이션의 로그를 수집/시각화하는 환경을 제공합니다.

## 아키텍처

```
Spring Boot App (dev/prod) 
    ↓ Logback → Logstash TCP (5000)
    ↓
Logstash → Elasticsearch (9200)
    ↓
Kibana (5601) ← 브라우저로 접근
```

## 디렉토리 구조

```
docker/
├── docker-compose.yml           # ELK 스택 + Spring Boot 앱 전체 구성
├── Dockerfile                   # Spring Boot 앱 이미지 빌드
├── logstash/
│   └── pipeline/
│       └── logstash.conf       # Logstash 파이프라인 설정
└── README.md                    # 이 파일
```

## 사전 요구사항

- Docker 및 Docker Compose 설치
- 최소 4GB 메모리 (Elasticsearch 요구사항)

## 실행 방법

### 1. 전체 스택 실행 (ELK + Dev + Prod 환경)

```bash
cd docker
docker-compose up -d
```

### 2. 특정 환경만 실행

**Dev 환경만:**
```bash
docker-compose up -d elasticsearch logstash kibana spring-app-dev
```

**Prod 환경만:**
```bash
docker-compose up -d elasticsearch logstash kibana spring-app-prod
```

**ELK 스택만 (로컬에서 Spring Boot 직접 실행 시):**
```bash
docker-compose up -d elasticsearch logstash kibana
```

### 3. 로그 확인

```bash
# 전체 서비스 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f spring-app-dev
docker-compose logs -f elasticsearch
docker-compose logs -f logstash
```

## 접속 정보

| 서비스 | URL | 설명 |
|--------|-----|------|
| **Kibana** | http://localhost:5601 | 로그 시각화 대시보드 |
| **Elasticsearch** | http://localhost:9200 | 로그 저장소 API |
| **Spring Boot (Dev)** | http://localhost:8080 | 개발 환경 애플리케이션 |
| **Spring Boot (Prod)** | http://localhost:8081 | 운영 환경 애플리케이션 |
| **Logstash** | localhost:5000 | 로그 수집 포트 (TCP/UDP) |

## Kibana 설정

### 자동 설정 (권장)

Kibana 시작 후 초기화 스크립트 실행:

```bash
cd docker
./kibana-init.sh
```

자동으로 설정되는 항목:
- Index Patterns: `spring-logs-dev-*`, `spring-logs-prod-*`, `spring-logs-*`
- Dashboard: "Spring Boot Application Dashboard" (4개 시각화 포함)
- Default Index Pattern: `spring-logs-*`

### 수동 설정 (선택)

<details>
<summary>Index Pattern 수동 생성 (클릭하여 펼치기)</summary>

1. Kibana 접속: http://localhost:5601
2. 메뉴 → Management → Stack Management → Index Patterns
3. "Create index pattern" 클릭
4. Index pattern 입력:
   - Dev 환경: `spring-logs-dev-*`
   - Prod 환경: `spring-logs-prod-*`
   - 전체: `spring-logs-*`
5. Timestamp field: `@timestamp` 선택
6. "Create index pattern" 클릭

</details>

### 로그 확인

1. 메뉴 → Discover
2. 좌측 상단에서 Index Pattern 선택 (기본값: `spring-logs-*`)
3. 시간 범위 조정 (우측 상단)
4. 필터 추가:
   - `environment: dev` (dev 환경만)
   - `environment: prod` (prod 환경만)
   - `level: ERROR` (에러 로그만)

### Dashboard 확인

1. 메뉴 → Dashboard
2. "Spring Boot Application Dashboard" 선택
3. 시각화 항목:
   - **Logs by Level**: 로그 레벨별 분포
   - **Request Performance**: 평균 응답 시간
   - **HTTP Status Distribution**: 상태 코드 분포
   - **Top Endpoints**: 최다 요청 엔드포인트

### 유용한 필드

- `serviceName`: 서비스 이름
- `version`: 애플리케이션 버전
- `environment`: 환경 (dev/prod)
- `level`: 로그 레벨 (INFO, WARN, ERROR)
- `logger_name`: 로거 이름 (패키지.클래스)
- `message`: 로그 메시지
- `traceId`, `spanId`, `parentSpanId`: 분산 추적 ID
- `requestUri`, `method`: HTTP 요청 정보
- `httpStatus`: HTTP 응답 상태 코드
- `duration`: 요청 처리 시간 (ms)
- `clientIp`: 클라이언트 IP

## 환경별 로그 설정

### Dev 환경
- **출력**: Console + Logstash (Elasticsearch)
- **로그 레벨**: INFO
- **포트**: 8080
- **인덱스**: `spring-logs-dev-YYYY.MM.dd`

### Prod 환경
- **출력**: Console + Logstash (Elasticsearch)
- **로그 레벨**: INFO
- **포트**: 8081
- **인덱스**: `spring-logs-prod-YYYY.MM.dd`

## 로컬에서 Spring Boot 실행 시

ELK 스택만 실행하고 Spring Boot는 로컬에서:

```bash
# 1. ELK 스택만 실행
cd docker
docker-compose up -d elasticsearch logstash kibana

# 2. 로컬에서 Spring Boot 실행 (dev 환경)
cd ..
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 (prod 환경)
./gradlew bootRun --args='--spring.profiles.active=prod'
```

로컬 실행 시 `application-dev.yml` 또는 `application-prod.yml`에 다음 설정 추가:

```yaml
logstash:
  host: localhost
  port: 5000
```

## 트러블슈팅

### Elasticsearch가 시작되지 않음
```bash
# 메모리 부족 시 docker-compose.yml에서 ES_JAVA_OPTS 조정
# 현재 설정: -Xms512m -Xmx512m
```

### Logstash 연결 오류
```bash
# Logstash 상태 확인
curl http://localhost:9600/_node/stats/pipelines

# Logstash 재시작
docker-compose restart logstash
```

### 로그가 Kibana에 보이지 않음
```bash
# 1. Elasticsearch 인덱스 확인
curl http://localhost:9200/_cat/indices?v

# 2. Logstash 로그 확인
docker-compose logs logstash

# 3. Spring Boot 로그 확인
docker-compose logs spring-app-dev
```

### Spring Boot 빌드 실패
```bash
# 빌드 캐시 삭제 후 재시도
docker-compose build --no-cache spring-app-dev
docker-compose build --no-cache spring-app-prod
```

## 정리

```bash
# 모든 컨테이너 중지 및 삭제
docker-compose down

# 볼륨까지 삭제 (Elasticsearch 데이터 삭제)
docker-compose down -v

# 이미지까지 삭제
docker-compose down --rmi all
```

## 추가 설정

### Logstash 파이프라인 커스터마이징

`logstash/pipeline/logstash.conf` 파일을 수정하여:
- 필터 규칙 추가
- 인덱스 이름 변경
- 추가 output 설정

변경 후 Logstash 재시작:
```bash
docker-compose restart logstash
```

### Spring Boot Logback 커스터마이징

`src/main/resources/logback-spring.xml` 파일을 수정하여:
- 로그 포맷 변경
- 로그 레벨 조정
- 추가 appender 설정

변경 후 Spring Boot 재시작:
```bash
docker-compose restart spring-app-dev
# 또는
docker-compose restart spring-app-prod
```
