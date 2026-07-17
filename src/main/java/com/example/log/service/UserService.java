package com.example.log.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public void findUser(Long id) {
        log.info("UserService - DB 조회 중");
        // 실제 DB 로직 시뮬레이션
        try {
            Thread.sleep(50); // DB 조회 시뮬레이션
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("UserService - DB 조회 완료");
    }
}
