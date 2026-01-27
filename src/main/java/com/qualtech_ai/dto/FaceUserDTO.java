package com.qualtech_ai.dto;

import com.qualtech_ai.entity.FaceUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaceUserDTO {
    private String id;
    private String name;
    private String email;
    private String faceEmbedding;
    private String imageUrl;
    private String s3Key;
    private String azureFaceId;
    private String awsFaceId;
    private String externalImageId;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String department;
    private String position;

    public static FaceUserDTO fromEntity(FaceUser faceUser) {
        FaceUserDTO dto = new FaceUserDTO();
        dto.setId(faceUser.getId());
        dto.setName(faceUser.getName());
        dto.setEmail(faceUser.getEmail());
        dto.setFaceEmbedding(faceUser.getFaceEmbedding());
        dto.setImageUrl(faceUser.getImageUrl());
        dto.setS3Key(faceUser.getS3Key());
        dto.setAzureFaceId(faceUser.getAzureFaceId());
        dto.setAwsFaceId(faceUser.getAwsFaceId());
        dto.setExternalImageId(faceUser.getExternalImageId());
        dto.setActive(faceUser.isActive());
        dto.setCreatedAt(faceUser.getCreatedAt());
        dto.setUpdatedAt(faceUser.getUpdatedAt());
        dto.setDepartment(faceUser.getDepartment());
        dto.setPosition(faceUser.getPosition());
        // Intentionally excluding imageData to prevent LOB stream issues
        return dto;
    }
}
