package com.qualtech_ai.service.impl;

import com.qualtech_ai.service.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3ServiceImpl(S3Client s3Client,
                        @Value("${aws.s3.bucket-name}") String bucketName,
                        @Value("${aws.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    @Override
    public String uploadFile(MultipartFile file, String key) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Error deleting file from S3: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateS3Key(String prefix, String originalFilename) {
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return String.format("%s/%s%s", prefix, UUID.randomUUID(), fileExtension);
    }

    @Override
    public String getPublicUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }
}
