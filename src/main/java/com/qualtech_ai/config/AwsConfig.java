package com.qualtech_ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

@Slf4j
@Configuration
public class AwsConfig {

        @Value("${aws.accessKeyId:}")
        private String accessKeyId;

        @Value("${aws.secretKey:}")
        private String secretKey;

        @Value("${aws.region:us-east-1}")
        private String region;

        private AwsCredentialsProvider getCredentialsProvider() {
                if (isInvalid(accessKeyId) || isInvalid(secretKey)) {
                        log.info("AWS Credentials not explicitly configured in application.yml. Using DefaultCredentialsProvider (Env vars, IAM roles, etc.).");
                        return DefaultCredentialsProvider.create();
                }
                return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretKey));
        }

        private Region getRegion() {
                return Region.of(region == null || region.isEmpty() || region.startsWith("YOUR_") ? "us-east-1"
                                : region);
        }

        @Bean
        public ComprehendClient comprehendClient() {
                try {
                        return ComprehendClient.builder()
                                        .region(getRegion())
                                        .credentialsProvider(getCredentialsProvider())
                                        .build();
                } catch (Exception e) {
                        log.warn("Failed to initialize AWS Comprehend Client: {}", e.getMessage());
                        return null;
                }
        }

        @Bean
        public S3Client s3Client() {
                try {
                        return S3Client.builder()
                                        .region(getRegion())
                                        .credentialsProvider(getCredentialsProvider())
                                        .overrideConfiguration(
                                                        software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
                                                                        .builder()
                                                                        .apiCallTimeout(java.time.Duration
                                                                                        .ofMinutes(20))
                                                                        .apiCallAttemptTimeout(java.time.Duration
                                                                                        .ofMinutes(20))
                                                                        .build())
                                        .build();
                } catch (Exception e) {
                        log.warn("Failed to initialize AWS S3 Client. Features will be disabled. Error: {}",
                                        e.getMessage());
                        return null;
                }
        }

        @Bean
        public TranscribeClient transcribeClient() {
                try {
                        return TranscribeClient.builder()
                                        .region(getRegion())
                                        .credentialsProvider(getCredentialsProvider())
                                        .build();
                } catch (Exception e) {
                        log.warn("Failed to initialize AWS Transcribe Client: {}", e.getMessage());
                        return null;
                }
        }

        @Bean
        public RekognitionClient rekognitionClient() {
                try {
                        return RekognitionClient.builder()
                                        .region(getRegion())
                                        .credentialsProvider(getCredentialsProvider())
                                        .build();
                } catch (Exception e) {
                        log.warn("Failed to initialize AWS Rekognition Client: {}", e.getMessage());
                        return null;
                }
        }

        private boolean isInvalid(String value) {
                return value == null || value.isBlank() || value.startsWith("YOUR_") || value.contains("PLACEHOLDER");
        }
}
