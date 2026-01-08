package com.qualtech_ai.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.TextAnalyticsClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfig {

    @Value("${azure.storage.connection-string}")
    private String storageConnectionString;

    @Value("${azure.speech.key}")
    private String speechKey;

    @Value("${azure.speech.region}")
    private String speechRegion;

    @Value("${azure.language.key}")
    private String languageKey;

    @Value("${azure.language.endpoint}")
    private String languageEndpoint;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (storageConnectionString == null || storageConnectionString.isEmpty()) {
            return null;
        }
        return new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
    }

    @Bean
    public TextAnalyticsClient textAnalyticsClient() {
        if (languageKey == null || languageKey.isEmpty() || languageEndpoint == null || languageEndpoint.isEmpty()) {
            return null;
        }
        return new TextAnalyticsClientBuilder()
                .credential(new AzureKeyCredential(languageKey))
                .endpoint(languageEndpoint)
                .buildClient();
    }

    @Bean
    public SpeechConfig speechConfig() {
        if (speechKey == null || speechKey.isEmpty() || speechRegion == null || speechRegion.isEmpty()) {
            return null;
        }
        return SpeechConfig.fromSubscription(speechKey, speechRegion);
    }
}
