package com.qualtech_ai.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.TextAnalyticsClientBuilder;
import com.azure.ai.vision.face.FaceClient;
import com.azure.ai.vision.face.FaceClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.policy.AddHeadersPolicy;
import com.azure.core.http.policy.RequestIdPolicy;
import com.azure.core.http.policy.UserAgentPolicy;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AzureConfig {

    private final AzureProperties azureProperties;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (isInvalid(azureProperties.getStorage().getConnectionString())) {
            return null;
        }
        try {
            return new BlobServiceClientBuilder()
                    .connectionString(azureProperties.getStorage().getConnectionString())
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
        String key = azureProperties.getLanguage().getKey();
        String endpoint = azureProperties.getLanguage().getEndpoint();
        if (isInvalid(key) || isInvalid(endpoint)) {
            return null;
        }
        try {
            return new TextAnalyticsClientBuilder()
                    .credential(new AzureKeyCredential(key))
                    .endpoint(endpoint)
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
        String key = azureProperties.getSpeech().getKey();
        String region = azureProperties.getSpeech().getRegion();
        if (isInvalid(key) || isInvalid(region)) {
            return null;
        }
        try {
            return SpeechConfig.fromSubscription(key, region);
        } catch (Exception e) {
            log.warn("Failed to initialize Azure Speech Config. Speech features will be disabled. Error: {}",
                    e.getMessage());
            return null;
        }
    }

    @Bean
    public FaceClient faceClient() {
        String key = azureProperties.getFace().getKey();
        String endpoint = azureProperties.getFace().getEndpoint();

        // DEBUG: Log Azure credentials status
        log.info("=== AZURE FACE API CONFIGURATION DEBUG ===");
        log.info("Face Key present: {}", key != null && !key.isEmpty());
        log.info("Face Key length: {}", key != null ? key.length() : 0);
        log.info("Face Key first 10 chars: {}",
                key != null && key.length() > 10 ? key.substring(0, 10) + "..." : "NULL");
        log.info("Face Endpoint: {}", endpoint);
        log.info("Is Invalid Check (Key): {}", isInvalid(key));
        log.info("Is Invalid Check (Endpoint): {}", isInvalid(endpoint));

        if (isInvalid(key) || isInvalid(endpoint)) {
            log.warn("Azure Face Client NOT initialized - missing or invalid credentials");
            return null;
        }
        try {
            // CRITICAL: Custom Pipeline to disable Retries and prevent Netty crashes
            // This fixes the "channel not registered to an event loop" error
            com.azure.core.http.HttpClient nettyClient = new com.azure.core.http.netty.NettyAsyncHttpClientBuilder()
                    .eventLoopGroup(new io.netty.channel.nio.NioEventLoopGroup(1)) // Dedicated loop
                    .build();

            com.azure.core.http.HttpPipeline pipeline = new com.azure.core.http.HttpPipelineBuilder()
                    .policies(
                            new UserAgentPolicy(),
                            new RequestIdPolicy(),
                            new AddHeadersPolicy(
                                    new HttpHeaders().set(HttpHeaderName.CONNECTION, "keep-alive"))
                    // EXPLICITLY REMOVED RetryPolicy to stop cascade failures
                    )
                    .httpClient(nettyClient)
                    .build();

            return new FaceClientBuilder()
                    .endpoint(endpoint)
                    .credential(new AzureKeyCredential(key))
                    .pipeline(pipeline)
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
