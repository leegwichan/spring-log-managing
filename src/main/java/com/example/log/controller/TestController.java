package com.example.log.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
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
}
