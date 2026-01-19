package com.qualtech_ai.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.TextAnalyticsClientBuilder;
import com.azure.ai.vision.face.FaceClient;
import com.azure.ai.vision.face.FaceClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AzureConfig {

    @Value("${azure.storage.connection-string:}")
    private String storageConnectionString;

    @Value("${azure.speech.key:}")
    private String speechKey;

    @Value("${azure.speech.region:}")
    private String speechRegion;

    @Value("${azure.language.key:}")
    private String languageKey;

    @Value("${azure.language.endpoint:}")
    private String languageEndpoint;

    @Value("${azure.face.key:}")
    private String faceKey;

    @Value("${azure.face.endpoint:}")
    private String faceEndpoint;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (isInvalid(storageConnectionString)) {
            return null;
        }
        try {
            return new BlobServiceClientBuilder()
                    .connectionString(storageConnectionString)
                    .buildClient();
        } catch (Exception e) {
            log.warn(
                    "Failed to initialize Azure Blob Service Client with provided connection string. Storage features will be disabled. Error: {}",
                    e.getMessage());
            return null;
        }
    }

    @Bean
    public TextAnalyticsClient textAnalyticsClient() {
        if (isInvalid(languageKey) || isInvalid(languageEndpoint)) {
            return null;
        }
        try {
            return new TextAnalyticsClientBuilder()
                    .credential(new AzureKeyCredential(languageKey))
                    .endpoint(languageEndpoint)
                    .buildClient();
        } catch (Exception e) {
            log.warn(
                    "Failed to initialize Azure Text Analytics Client. Text analysis features will be disabled. Error: {}",
                    e.getMessage());
            return null;
        }
    }

    @Bean
    public SpeechConfig speechConfig() {
        if (isInvalid(speechKey) || isInvalid(speechRegion)) {
            return null;
        }
        try {
            return SpeechConfig.fromSubscription(speechKey, speechRegion);
        } catch (Exception e) {
            log.warn("Failed to initialize Azure Speech Config. Speech features will be disabled. Error: {}",
                    e.getMessage());
            return null;
        }
    }

    @Bean
    public FaceClient faceClient() {
        if (isInvalid(faceKey) || isInvalid(faceEndpoint)) {
            return null;
        }
        try {
            return new FaceClientBuilder()
                    .endpoint(faceEndpoint)
                    .credential(new AzureKeyCredential(faceKey))
                    .buildClient();
        } catch (Exception e) {
            log.warn("Failed to initialize Azure Face Client. Face features will be disabled. Error: {}",
                    e.getMessage());
            return null;
        }
    }

    private boolean isInvalid(String value) {
        return value == null || value.isBlank() || value.startsWith("YOUR_") || value.contains("PLACEHOLDER");
    }
}
