package com.qualtech_ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SentimentRequest {
    @NotBlank(message = "Text cannot be empty")
    private String text;

    private String provider;
}
