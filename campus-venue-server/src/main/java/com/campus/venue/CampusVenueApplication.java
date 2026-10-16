package com.campus.venue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园场馆预约与拼场组队系统 - 启动类
 */
@SpringBootApplication
@EnableScheduling
public class CampusVenueApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusVenueApplication.class, args);
    }
}
