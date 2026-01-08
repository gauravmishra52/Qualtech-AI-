package com.qualtech_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SentimentResponse {
    private String sentiment;
    private Map<String, Float> sentimentScore;
}
