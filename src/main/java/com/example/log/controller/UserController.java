package com.example.log.controller;

import com.example.log.service.ExternalApiService;
import com.example.log.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    private final ExternalApiService externalApiService;

    public UserController(UserService userService, ExternalApiService externalApiService) {
        this.userService = userService;
        this.externalApiService = externalApiService;
    }

    @GetMapping("/users/{id}")
    public String getUser(@PathVariable Long id) {
        log.info("UserController - 사용자 조회 시작");
        userService.findUser(id);
        log.info("UserController - 사용자 조회 완료");
        return "User: " + id;
    }

    /**
     * Test endpoint to demonstrate distributed tracing
     */
    @GetMapping("/trace-test")
    public Map<String, String> traceTest(@RequestParam(required = false) String externalUrl) {
        log.info("Trace test endpoint called");

        Map<String, String> response = new HashMap<>();
        response.put("traceId", MDC.get("traceId"));
        response.put("spanId", MDC.get("spanId"));
        response.put("parentSpanId", MDC.get("parentSpanId"));
        response.put("serviceName", MDC.get("serviceName"));
        response.put("instanceId", MDC.get("instanceId"));
        response.put("version", MDC.get("version"));
        response.put("environment", MDC.get("environment"));

        // Call external API if URL is provided
        if (externalUrl != null && !externalUrl.isEmpty()) {
            try {
                String externalResponse = externalApiService.callExternalService(externalUrl);
                response.put("externalApiResponse", externalResponse);
            } catch (Exception e) {
                response.put("externalApiError", e.getMessage());
            }
        }

        return response;
    }
}
