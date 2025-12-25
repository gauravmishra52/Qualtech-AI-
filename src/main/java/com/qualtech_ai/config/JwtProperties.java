package com.qualtech_ai.config;

import lombok.Getter;
import lombok.Setter;

// @Configuration
// @ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long expiration;
}
