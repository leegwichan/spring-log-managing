package com.example.log.controller;

import com.example.log.service.ExternalApiService;
import com.example.log.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {

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

    @GetMapping("/call-external")
    public Map<String, String> callExternal(@RequestParam(required = false) String externalUrl) {
        log.info("외부 API 호출 요청");
        Map<String, String> response = new HashMap<>();

        if (externalUrl != null && !externalUrl.isEmpty()) {
            try {
                String externalResponse = externalApiService.callExternalService(externalUrl);
                response.put("externalApiResponse", externalResponse);
            } catch (Exception e) {
                response.put("externalApiError", e.getMessage());
            }
        }

        response.put("traceId", MDC.get("traceId"));
        response.put("spanId", MDC.get("spanId"));
        response.put("parentSpanId", MDC.get("parentSpanId"));
        response.put("serviceName", MDC.get("serviceName"));
        response.put("instanceId", MDC.get("instanceId"));
        response.put("version", MDC.get("version"));
        response.put("environment", MDC.get("environment"));
        return response;
    }
}
