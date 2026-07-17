# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.1.0 application for structured logging with distributed tracing, built with Java 21 and Gradle 9.5.1. The application demonstrates JSON-structured logging, MDC-based context propagation, and integration with the ELK (Elasticsearch, Logstash, Kibana) stack.

**Group:** `com.example.log`  
**Root Package:** `com.example.log`

## Build & Development Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the Spring Boot application (local profile)
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
./gradlew bootRun --args='--spring.profiles.active=prod'

# Create executable JAR
./gradlew bootJar

# Run a single test class
./gradlew test --tests com.example.log.SpringLogManagingApplicationTests
```

## Docker & ELK Stack

This project includes a full ELK stack configuration for log aggregation and visualization.

```bash
# Start entire ELK stack + Spring Boot (dev & prod)
cd docker
docker-compose up -d

# Start only ELK stack (for local Spring Boot development)
docker-compose up -d elasticsearch logstash kibana

# Start specific environment
docker-compose up -d elasticsearch logstash kibana spring-app-dev

# View logs
docker-compose logs -f spring-app-dev

# Stop and remove all containers
docker-compose down

# Stop and remove including volumes (deletes Elasticsearch data)
docker-compose down -v
```

**Access URLs:**
- Kibana (log visualization): http://localhost:5601
- Elasticsearch: http://localhost:9200
- Spring Boot Dev: http://localhost:8080
- Spring Boot Prod: http://localhost:8081
- Logstash TCP: localhost:5001 (mapped from container port 5000)

## Architecture

### Logging Architecture

This application implements structured logging with distributed tracing:

1. **MdcLoggingFilter** (filter layer, `@Order(1)`)
   - Populates MDC context for every request
   - Generates/propagates distributed tracing IDs: `traceId`, `spanId`, `parentSpanId`
   - Captures service identity: `serviceName`, `instanceId`, `version`, `environment`
   - Records request metadata: `requestUri`, `method`, `clientIp`
   - Measures performance: `duration`, `httpStatus`
   - Adds tracing headers to response (`X-Trace-Id`, `X-Span-Id`)

2. **Logback Configuration** (`logback-spring.xml`)
   - Profile-specific configurations: `dev`, `prod`
   - Dual output: Console + Logstash TCP appender
   - Async appenders for non-blocking logging
   - JSON-formatted logs via `LogstashEncoder`
   - Elasticsearch index pattern: `spring-logs-{environment}-YYYY.MM.dd`

3. **Service Configuration**
   - `ServiceInfoProperties` binds `app.service.*` properties
   - Environment-specific YAML files: `application-dev.yml`, `application-prod.yml`
   - Logstash connection configurable via environment variables: `LOGSTASH_HOST`, `LOGSTASH_PORT`

### Component Structure

```
com.example.log/
├── config/              # Spring configuration
│   ├── ServiceInfoProperties    # app.service.* property binding
│   ├── WebMvcConfig             # Interceptor registration
│   └── RestTemplateConfig       # RestTemplate with interceptors
├── controller/          # REST endpoints
├── filter/              # Servlet filters
│   └── MdcLoggingFilter         # MDC context initialization (@Order(1))
├── interceptor/         # Request/response interceptors
│   ├── PerformanceLoggingInterceptor  # Performance metrics
│   └── TracingInterceptor             # Distributed tracing propagation
├── service/             # Business logic
└── exception/           # Exception handling
    └── GlobalExceptionHandler
```

## Key Dependencies

- **Spring Boot Starter WebMVC** - Web framework with embedded Tomcat
- **Logstash Logback Encoder** (`net.logstash.logback:logstash-logback-encoder:8.0`) - JSON log formatting
- **Logback Classic** (`ch.qos.logback:logback-classic`) - Logging implementation
- **Lombok** - Code generation (compile-only)
- **JUnit 5** - Testing framework

## Development Notes

- Java 21 is required (configured via Gradle toolchain)
- Main application class: `SpringLogManagingApplication`
- Default port: 8080 (configurable per profile)
- All HTTP requests automatically populate MDC context via `MdcLoggingFilter`
- Trace IDs propagate via headers: `X-Trace-Id`, `X-Span-Id`, `X-Parent-Span-Id`
- Use `MDC.get("traceId")` in application code to access current trace ID
- Logback profiles match Spring profiles (`dev`, `prod`)
