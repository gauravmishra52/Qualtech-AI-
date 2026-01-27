package com.qualtech_ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_users")
@Data
public class FaceUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String faceEmbedding; // Store face embedding as JSON string

    @Column(name = "image_url")
    private String imageUrl; // URL to the image in S3

    @Column(name = "s3_key")
    private String s3Key; // S3 object key

    @Column(name = "azure_face_id")
    private String azureFaceId;

    @Column(name = "aws_face_id", unique = true) // CRITICAL: Must be unique for reliable lookup
    private String awsFaceId;

    @Column(name = "external_image_id", unique = true)
    private String externalImageId;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image_data", columnDefinition = "TEXT")
    private String imageData; // Base64 encoded image for display

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Add any additional fields as needed
    private String department;
    private String position;

    // Add any custom methods or relationships here
}
