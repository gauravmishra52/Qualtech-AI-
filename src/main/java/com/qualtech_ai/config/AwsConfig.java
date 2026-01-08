package com.qualtech_ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

@Configuration
public class AwsConfig {

        @Value("${aws.accessKeyId}")
        private String accessKeyId;

        @Value("${aws.secretKey}")
        private String secretKey;

        @Value("${aws.region}")
        private String region;

        private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider getCredentialsProvider() {
                return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretKey));
        }

        private Region getRegion() {
                return Region.of(region);
        }

        @Bean
        public ComprehendClient comprehendClient() {
                return ComprehendClient.builder()
                                .region(getRegion())
                                .credentialsProvider(getCredentialsProvider())
                                .build();
        }

        @Bean
        public S3Client s3Client() {
                return S3Client.builder()
                                .region(getRegion())
                                .credentialsProvider(getCredentialsProvider())
                                .overrideConfiguration(
                                                software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
                                                                .builder()
                                                                .apiCallTimeout(java.time.Duration.ofMinutes(20))
                                                                .apiCallAttemptTimeout(java.time.Duration.ofMinutes(20))
                                                                .build())
                                .build();
        }

        @Bean
        public TranscribeClient transcribeClient() {
                return TranscribeClient.builder()
                                .region(getRegion())
                                .credentialsProvider(getCredentialsProvider())
                                .build();
        }
}
