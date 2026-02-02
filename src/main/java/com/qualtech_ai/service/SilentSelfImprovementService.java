package com.qualtech_ai.service;

import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.repository.FaceUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class SilentSelfImprovementService {

    @Value("${face.self-improvement.high-confidence-threshold:0.95}")
    private double highConfidenceThreshold;

    @Value("${face.self-improvement.min-attempts:5}")
    private int minAttemptsBeforeUpdate;

    private final FaceUserRepository faceUserRepository;
    private final ConcurrentHashMap<String, AtomicLong> userAttemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> userAverageConfidence = new ConcurrentHashMap<>();

    public SilentSelfImprovementService(FaceUserRepository faceUserRepository) {
        this.faceUserRepository = faceUserRepository;
    }

    @Transactional
    public void recordHighConfidenceMatch(@org.springframework.lang.NonNull String userId, double confidence,
            String newFaceEmbedding) {
        if (confidence < highConfidenceThreshold) {
            return;
        }

        userAttemptCounts.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();

        userAverageConfidence.compute(userId, (k, currentAvg) -> {
            if (currentAvg == null) {
                return confidence;
            } else {
                return (currentAvg * 0.9) + (confidence * 0.1);
            }
        });

        long attempts = userAttemptCounts.get(userId).get();
        double avgConfidence = userAverageConfidence.get(userId);

        if (attempts >= minAttemptsBeforeUpdate && avgConfidence > highConfidenceThreshold) {
            updateFaceEmbeddingSilently(userId, newFaceEmbedding, confidence);
        }

        log.debug("Recorded high confidence match - User: {}, Confidence: {}, Attempts: {}, Avg: {}",
                userId, confidence, attempts, avgConfidence);
    }

    @Transactional
    private void updateFaceEmbeddingSilently(@org.springframework.lang.NonNull String userId, String newFaceEmbedding,
            double confidence) {
        try {

            FaceUser user = faceUserRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setFaceEmbedding(newFaceEmbedding);
                faceUserRepository.save(user);

                log.info("Silent self-improvement: Updated face embedding for user {} (confidence: {})",
                        user.getEmail(), confidence);

                // Reset counters after successful update
                userAttemptCounts.remove(userId);
                userAverageConfidence.remove(userId);
            }
        } catch (Exception e) {
            log.error("Failed to update face embedding for user {}: {}", userId, e.getMessage());
        }
    }

    public void resetUserHistory(String userId) {
        userAttemptCounts.remove(userId);
        userAverageConfidence.remove(userId);
        log.debug("Reset self-improvement history for user: {}", userId);
    }

    public boolean shouldUpdateEmbedding(String userId, double confidence) {
        Long attempts = userAttemptCounts.get(userId) != null ? userAttemptCounts.get(userId).get() : 0L;
        Double avgConfidence = userAverageConfidence.get(userId);

        return confidence >= highConfidenceThreshold &&
                attempts >= minAttemptsBeforeUpdate &&
                avgConfidence != null &&
                avgConfidence > highConfidenceThreshold;
    }

    public double getHighConfidenceThreshold() {
        return highConfidenceThreshold;
    }

    public int getMinAttemptsBeforeUpdate() {
        return minAttemptsBeforeUpdate;
    }
}
