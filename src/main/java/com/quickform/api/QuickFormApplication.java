package com.quickform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.quickform.api.mapper")
public class QuickFormApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickFormApplication.class, args);
    }
}
