package com.qualtech_ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualtech_ai.dto.FaceRegistrationRequest;
import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.repository.FaceUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceUserTxService {

    private final FaceUserRepository faceUserRepository;

    @Transactional
    public FaceUser createUser(FaceRegistrationRequest request, String imageBase64) {
        log.debug("Creating new user with email: {}", request.getEmail());

        FaceUser user = new FaceUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setDepartment(request.getDepartment());
        user.setPosition(request.getPosition());
        user.setImageData(imageBase64);

        return faceUserRepository.save(user);
    }

    @Transactional
    public void saveEmbedding(String userId, String embedding) {
        Objects.requireNonNull(userId, "userId cannot be null");
        log.debug("Saving face embedding for user: {}", userId);

        FaceUser user = faceUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Store embedding as JSON list for future multiple embeddings support
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            List<String> embeddingList = new ArrayList<>();
            embeddingList.add(embedding);
            user.setFaceEmbedding(objectMapper.writeValueAsString(embeddingList));
        } catch (Exception e) {
            log.error("Failed to serialize embeddings, falling back to simple string", e);
            user.setFaceEmbedding(embedding);
        }

        faceUserRepository.save(user);
    }

    @Transactional
    public void updateAwsInfo(String userId, String awsFaceId, String externalId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        log.debug("Updating AWS info for user: {} (FaceId: {}, ExternalId: {})", userId, awsFaceId, externalId);

        FaceUser user = faceUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setAwsFaceId(awsFaceId);
        user.setExternalImageId(externalId);

        faceUserRepository.save(user);
    }

    @Transactional
    public void updateS3Info(String userId, String imageUrl, String s3Key) {
        Objects.requireNonNull(userId, "userId cannot be null");
        log.debug("Updating S3 info for user: {} (URL: {}, Key: {})", userId, imageUrl, s3Key);

        FaceUser user = faceUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setImageUrl(imageUrl);
        user.setS3Key(s3Key);

        faceUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return faceUserRepository.existsByEmail(email);
    }
}
