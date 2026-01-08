package com.qualtech_ai.service;

import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.models.DocumentSentiment;
import com.azure.ai.textanalytics.models.SentimentConfidenceScores;
import com.qualtech_ai.dto.SentimentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AzureSentimentService {

    private static final Logger log = LoggerFactory.getLogger(AzureSentimentService.class);
    private final TextAnalyticsClient textAnalyticsClient;

    public AzureSentimentService(TextAnalyticsClient textAnalyticsClient) {
        this.textAnalyticsClient = textAnalyticsClient;
    }

    public SentimentResponse analyzeSentiment(String text) {
        if (textAnalyticsClient == null) {
            throw new RuntimeException("Azure Text Analytics is not configured.");
        }

        if (text == null || text.isBlank()) {
            Map<String, Float> emptyScores = new HashMap<>();
            emptyScores.put("Positive", 0.0f);
            emptyScores.put("Negative", 0.0f);
            emptyScores.put("Neutral", 0.0f);
            emptyScores.put("Mixed", 0.0f);
            return new SentimentResponse("NEUTRAL", emptyScores);
        }

        log.info("Analyzing sentiment with Azure for text length: {}", text.length());

        // Azure limit is 5120 characters. Using 5000 for safety.
        int chunkSize = 5000;
        java.util.List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }

        double totalPositive = 0;
        double totalNegative = 0;
        double totalNeutral = 0;
        int count = 0;

        for (String chunk : chunks) {
            try {
                DocumentSentiment documentSentiment = textAnalyticsClient.analyzeSentiment(chunk);
                SentimentConfidenceScores scores = documentSentiment.getConfidenceScores();
                totalPositive += scores.getPositive();
                totalNegative += scores.getNegative();
                totalNeutral += scores.getNeutral();
                count++;
            } catch (Exception e) {
                log.error("Azure Sentiment Error on chunk: {}", e.getMessage(), e);
                throw new RuntimeException("Azure Sentiment Error on chunk: " + e.getMessage(), e);
            }
        }

        if (count == 0)
            throw new RuntimeException("No Azure sentiment data could be analyzed");

        Map<String, Float> scoreMap = new HashMap<>();
        scoreMap.put("Positive", (float) (totalPositive / count));
        scoreMap.put("Negative", (float) (totalNegative / count));
        scoreMap.put("Neutral", (float) (totalNeutral / count));
        scoreMap.put("Mixed", 0.0f); // Azure doesn't have a direct "Mixed" confidence score like AWS

        // Determine dominant sentiment
        String dominantSentiment = "NEUTRAL";
        float maxScore = -1;
        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                dominantSentiment = entry.getKey().toUpperCase();
            }
        }

        return new SentimentResponse(dominantSentiment, scoreMap);
    }
}
