package com.qualtech_ai.config;

import lombok.Data;

// @Configuration
// @ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {
    private String secret;
    private long expiration;

}
