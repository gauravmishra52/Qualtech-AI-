package com.qualtech_ai.service;

import com.qualtech_ai.dto.SentimentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.comprehend.model.SentimentScore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SentimentAnalysisService {

    private final ComprehendClient comprehendClient;
    private final AzureSentimentService azureSentimentService;

    @Value("${analysis.provider:aws}")
    private String provider;

    public SentimentAnalysisService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ComprehendClient comprehendClient,
            AzureSentimentService azureSentimentService) {
        this.comprehendClient = comprehendClient;
        this.azureSentimentService = azureSentimentService;
    }

    public SentimentResponse analyzeSentiment(String text, String activeProvider) {
        // Use requested provider if present, otherwise fall back to default from config
        activeProvider = (activeProvider != null && !activeProvider.isEmpty()) ? activeProvider : provider;

        if ("azure".equalsIgnoreCase(activeProvider)) {
            return azureSentimentService.analyzeSentiment(text);
        }

        // Default to AWS
        if (text == null || text.isBlank()) {
            Map<String, Float> emptyScores = new HashMap<>();
            emptyScores.put("Positive", 0.0f);
            emptyScores.put("Negative", 0.0f);
            emptyScores.put("Neutral", 0.0f);
            emptyScores.put("Mixed", 0.0f);
            emptyScores.put("Mixed", 0.0f);
            return new SentimentResponse("NEUTRAL", emptyScores);
        }

        if (comprehendClient == null) {
            throw new RuntimeException("AWS Comprehend is not configured. Please check your .env file.");
        }

        // AWS Comprehend limit is 5000 bytes. We use 4000 characters to be safe.
        int chunkSize = 4000;
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }

        float totalPositive = 0;
        float totalNegative = 0;
        float totalNeutral = 0;
        float totalMixed = 0;
        int count = 0;

        for (String chunk : chunks) {
            DetectSentimentRequest detectSentimentRequest = DetectSentimentRequest.builder()
                    .text(chunk)
                    .languageCode("en")
                    .build();

            try {
                DetectSentimentResponse response = comprehendClient.detectSentiment(detectSentimentRequest);
                SentimentScore score = response.sentimentScore();
                totalPositive += score.positive();
                totalNegative += score.negative();
                totalNeutral += score.neutral();
                totalMixed += score.mixed();
                count++;
            } catch (software.amazon.awssdk.services.comprehend.model.ComprehendException e) {
                // Log and continue or throw? Throwing for now to avoid partial/skewed results
                throw new RuntimeException("AWS Comprehend Error on chunk: " + e.awsErrorDetails().errorMessage(), e);
            }
        }

        if (count == 0)
            throw new RuntimeException("No sentiment data could be analyzed");

        Map<String, Float> scoreMap = new HashMap<>();
        scoreMap.put("Positive", totalPositive / count);
        scoreMap.put("Negative", totalNegative / count);
        scoreMap.put("Neutral", totalNeutral / count);
        scoreMap.put("Mixed", totalMixed / count);

        // Determine dominant sentiment based on averages
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
