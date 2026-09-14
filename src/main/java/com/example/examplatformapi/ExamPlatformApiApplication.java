package com.example.examplatformapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExamPlatformApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPlatformApiApplication.class, args);
    }
}