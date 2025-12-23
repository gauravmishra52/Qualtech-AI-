package com.qualtech_ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.qualtech_ai.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class QualtechAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(QualtechAiApplication.class, args);
    }
}

