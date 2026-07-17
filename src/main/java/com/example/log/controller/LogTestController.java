package com.example.log.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
