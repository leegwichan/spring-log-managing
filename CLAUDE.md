# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.1.0 application for log management, built with Java 21 and Gradle 9.5.1.

**Group:** `com.example.log`  
**Root Package:** `com.example.log`

## Build & Development Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the Spring Boot application
./gradlew bootRun

# Clean build artifacts
./gradlew clean

# Create executable JAR
./gradlew bootJar

# Build OCI container image
./gradlew bootBuildImage

# Run a single test class
./gradlew test --tests com.example.log.SpringLogManagingApplicationTests

# Run a specific test method
./gradlew test --tests com.example.log.SomeTest.testMethod
```

## Project Structure

```
src/main/java/com/example/log/      # Application source code
src/main/resources/                 # Configuration and static resources
  ├── application.properties        # Spring Boot configuration
  ├── static/                       # Static web resources
  └── templates/                    # Template files (Thymeleaf, etc.)
src/test/java/com/example/log/      # Test source code
```

## Key Dependencies

- **Spring Boot Starter WebMVC** - Web application framework with embedded Tomcat
- **JUnit 5** - Testing framework (via spring-boot-starter-webmvc-test)

## Development Notes

- Java 21 is required (configured via Gradle toolchain)
- Main application class: `SpringLogManagingApplication`
- Default Spring Boot port: 8080 (unless overridden in application.properties)
- Gradle wrapper is included; use `./gradlew` instead of system Gradle
