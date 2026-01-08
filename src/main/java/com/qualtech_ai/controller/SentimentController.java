package com.qualtech_ai.controller;

import com.qualtech_ai.dto.SentimentRequest;
import com.qualtech_ai.dto.SentimentResponse;
import com.qualtech_ai.service.SentimentAnalysisService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

    private static final Logger logger = LoggerFactory.getLogger(SentimentController.class);
    private final SentimentAnalysisService sentimentAnalysisService;

    public SentimentController(SentimentAnalysisService sentimentAnalysisService) {
        this.sentimentAnalysisService = sentimentAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<SentimentResponse> analyzeSentiment(@Valid @RequestBody SentimentRequest request) {
        logger.info("Analyzing sentiment for text: {} using provider: {}", request.getText(), request.getProvider());
        SentimentResponse response = sentimentAnalysisService.analyzeSentiment(request.getText(),
                request.getProvider());
        logger.info("Sentiment analysis result: {}", response.getSentiment());
        return ResponseEntity.ok(response);
    }
}
