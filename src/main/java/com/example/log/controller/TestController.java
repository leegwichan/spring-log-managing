package com.example.log.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Slf4j
public class TestController {


    @GetMapping("/normal")
    public String normalApi() {
        return "정상 응답";
    }

    @GetMapping("/slow")
    public String slowApi() throws InterruptedException {
        Thread.sleep(4000);  // 4초 대기
        return "느린 응답";
    }

    @GetMapping("/error")
    public String errorApi() {
        throw new RuntimeException("의도적 예외");
    }

    @GetMapping("/health")
    public String health() {
        // 이 API는 인터셉터에서 제외됨
        return "OK";
    }

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
