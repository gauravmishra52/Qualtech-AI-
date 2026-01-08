package com.qualtech_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoResult {
    private String fileName;
    private String transcript;
    private SentimentResponse sentimentAnalysis;
}
