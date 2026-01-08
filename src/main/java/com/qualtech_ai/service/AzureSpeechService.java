package com.qualtech_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.cognitiveservices.speech.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

@Service
public class AzureSpeechService {

    private static final Logger log = LoggerFactory.getLogger(AzureSpeechService.class);
    private final SpeechConfig speechConfig;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureSpeechService(SpeechConfig speechConfig) {
        this.speechConfig = speechConfig;
    }

    public String transcribeFile(String audioUrl) {
        if (speechConfig == null) {
            throw new RuntimeException("Azure Speech Service is not configured.");
        }

        // Extract key and region from speechConfig properties
        String subscriptionKey = speechConfig.getProperty(PropertyId.SpeechServiceConnection_Key);
        String region = speechConfig.getProperty(PropertyId.SpeechServiceConnection_Region);

        // Note: Using v3.1 of the Batch Transcription API
        String endpoint = "https://" + region + ".api.cognitive.microsoft.com/speechtotext/v3.1/transcriptions";

        log.info("Starting real Azure Batch Transcription for URL: {}", audioUrl);

        try {
            // 1. Submit Transcription Job
            Map<String, Object> body = Map.of(
                    "contentUrls", new String[] { audioUrl },
                    "locale", "en-US",
                    "displayName", "Transcription-" + UUID.randomUUID(),
                    "properties", Map.of(
                            "wordLevelTimestampsEnabled", false,
                            "punctuationMode", "DictatedAndAutomatic"));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                log.error("Failed to submit Azure transcription: {}", response.body());
                throw new RuntimeException(
                        "Azure Batch Transcription Submission Failed: Status " + response.statusCode());
            }

            String selfUrl = objectMapper.readTree(response.body()).get("self").asText();
            log.info("Azure Transcription Job Submitted: {}", selfUrl);

            // 2. Poll for Completion
            String resultFilesUrl = null;
            int maxRetries = 600; // ~20 minutes
            while (maxRetries-- > 0) {
                HttpRequest statusRequest = HttpRequest.newBuilder()
                        .uri(URI.create(selfUrl))
                        .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                        .GET()
                        .build();

                HttpResponse<String> statusResponse = httpClient.send(statusRequest,
                        HttpResponse.BodyHandlers.ofString());
                JsonNode statusNode = objectMapper.readTree(statusResponse.body());
                String status = statusNode.get("status").asText();

                if ("Succeeded".equals(status)) {
                    resultFilesUrl = statusNode.get("links").get("files").asText();
                    break;
                } else if ("Failed".equals(status)) {
                    throw new RuntimeException("Azure Transcription Job Failed: " + statusResponse.body());
                }

                log.debug("Waiting for Azure transcription (status: {})...", status);
                Thread.sleep(2000);
            }

            if (resultFilesUrl == null) {
                throw new RuntimeException("Azure Transcription Job Timed Out");
            }

            // 3. Get Result File URL
            HttpRequest resultRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resultFilesUrl))
                    .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .GET()
                    .build();

            HttpResponse<String> resultResponse = httpClient.send(resultRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode resultFiles = objectMapper.readTree(resultResponse.body()).get("values");

            String contentUrl = null;
            for (JsonNode file : resultFiles) {
                if ("Transcription".equals(file.path("kind").asText())) {
                    contentUrl = file.path("links").path("contentUrl").asText();
                    break;
                }
            }

            if (contentUrl == null || contentUrl.isEmpty()) {
                throw new RuntimeException("Could not find transcription content URL in result files");
            }

            // 4. Download and Parse JSON
            HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(contentUrl))
                    .GET()
                    .build();

            HttpResponse<String> downloadResponse = httpClient.send(downloadRequest,
                    HttpResponse.BodyHandlers.ofString());
            JsonNode finalJson = objectMapper.readTree(downloadResponse.body());

            StringBuilder fullTranscript = new StringBuilder();
            JsonNode combinedPhrases = finalJson.get("combinedPhrases");
            if (combinedPhrases != null && combinedPhrases.isArray()) {
                for (JsonNode phrase : combinedPhrases) {
                    fullTranscript.append(phrase.path("display").asText()).append(" ");
                }
            }

            return fullTranscript.toString().trim();

        } catch (Exception e) {
            log.error("Azure Batch Transcription Error: {}", e.getMessage(), e);
            throw new RuntimeException("Azure Speech Error: " + e.getMessage(), e);
        }
    }
}
