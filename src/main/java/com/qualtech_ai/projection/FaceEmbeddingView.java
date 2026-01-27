package com.qualtech_ai.projection;

/**
 * Projection interface for retrieving only face embeddings from the database.
 * This avoids loading LOB data (imageData) and improves performance for duplicate checks.
 */
public interface FaceEmbeddingView {
    String getFaceEmbedding();
}
