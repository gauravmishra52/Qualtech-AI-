package com.qualtech_ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface S3Service {
    /**
     * Uploads a file to S3
     * @param file The file to upload
     * @param key The S3 object key
     * @return The public URL of the uploaded file
     * @throws IOException If an I/O error occurs
     */
    String uploadFile(MultipartFile file, String key) throws IOException;

    /**
     * Deletes a file from S3
     * @param key The S3 object key
     */
    void deleteFile(String key);

    /**
     * Generates a unique S3 key for a file
     * @param prefix The prefix for the key (e.g., "faces/")
     * @param originalFilename The original filename
     * @return A unique S3 key
     */
    String generateS3Key(String prefix, String originalFilename);

    /**
     * Gets the public URL for an S3 object
     * @param key The S3 object key
     * @return The public URL
     */
    String getPublicUrl(String key);
}
