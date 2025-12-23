package com.qualtech_ai.util;

import java.util.UUID;

public class TokenGenerator {

    private TokenGenerator() {
        // utility class
    }

    public static String generateToken() {
        return UUID.randomUUID().toString();
    }
}

