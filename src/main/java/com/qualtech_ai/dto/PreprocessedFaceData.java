package com.qualtech_ai.dto;

/**
 * DTO for preprocessed face data containing the base64 image and face embedding.
 * This is used to pass face processing results between methods without database operations.
 */
public record PreprocessedFaceData(
    String base64Image,
    String faceEmbedding
) {}
