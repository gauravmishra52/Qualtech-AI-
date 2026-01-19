package com.qualtech_ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AzureConfigurationValidator {

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

    @EventListener(ApplicationReadyEvent.class)
    public void validateAzureConfiguration() {
        log.info("Validating Azure service configuration...");
        
        boolean hasIssues = false;

        if (isInvalid(storageConnectionString)) {
            log.warn("⚠️ Azure Blob Storage is not configured - storage connection string is missing");
            hasIssues = true;
        } else {
            log.info("✅ Azure Blob Storage is configured");
        }

        if (isInvalid(speechKey) || isInvalid(speechRegion)) {
            log.warn("⚠️ Azure Speech Service is not configured - key or region is missing");
            hasIssues = true;
        } else {
            log.info("✅ Azure Speech Service is configured");
        }

        if (isInvalid(languageKey) || isInvalid(languageEndpoint)) {
            log.warn("⚠️ Azure Language Service is not configured - key or endpoint is missing");
            hasIssues = true;
        } else {
            log.info("✅ Azure Language Service is configured");
        }

        if (isInvalid(faceKey) || isInvalid(faceEndpoint)) {
            log.warn("⚠️ Azure Face Service is not configured - key or endpoint is missing");
            hasIssues = true;
        } else {
            log.info("✅ Azure Face Service is configured");
        }

        if (hasIssues) {
            log.warn("\n🔧 To configure Azure services, set the following environment variables:");
            log.warn("   AZURE_STORAGE_CONNECTION_STRING");
            log.warn("   AZURE_SPEECH_KEY");
            log.warn("   AZURE_SPEECH_REGION");
            log.warn("   AZURE_LANGUAGE_KEY");
            log.warn("   AZURE_LANGUAGE_ENDPOINT");
            log.warn("   AZURE_FACE_KEY");
            log.warn("   AZURE_FACE_ENDPOINT");
            log.warn("\n📖 For more information, see: https://docs.microsoft.com/en-us/azure/cognitive-services/");
        } else {
            log.info("🎉 All Azure services are properly configured!");
        }
    }

    private boolean isInvalid(String value) {
        return value == null || value.isBlank() || value.startsWith("YOUR_") || value.contains("PLACEHOLDER");
    }
}
