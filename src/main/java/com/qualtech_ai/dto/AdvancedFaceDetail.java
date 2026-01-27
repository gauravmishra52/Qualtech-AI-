package com.qualtech_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.rekognition.model.BoundingBox;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedFaceDetail {
    private BoundingBox boundingBox;

    // Quality metrics
    private Double sharpness;
    private Double brightness;

    // Pose information
    private Double pitch;
    private Double roll;
    private Double yaw;

    // Occlusion level (0-1, higher means more occluded)
    private Double occlusionLevel;

    // Emotion analysis
    private String topEmotion;
    private Float emotionConfidence;

    // Age range
    private Integer ageLow;
    private Integer ageHigh;

    // Gender information
    private String gender;
    private Float genderConfidence;

    // Facial features
    private Boolean hasBeard;
    private Float beardConfidence;
    private Boolean hasMustache;
    private Float mustacheConfidence;
    private Boolean wearingEyeglasses;
    private Float eyeglassesConfidence;
    private Boolean wearingSunglasses;
    private Float sunglassesConfidence;

    // Expression detection
    private Boolean isSmiling;
    private Float smileConfidence;
    private Boolean eyesOpen;
    private Float eyesOpenConfidence;
    private Boolean mouthOpen;
    private Float mouthOpenConfidence;

    // Anti-spoofing metrics
    private Double spoofProbability; // 0-1, higher means more likely to be spoof

    /**
     * Check if this face is likely a spoof based on probability threshold
     */
    public boolean isLikelySpoof() {
        return spoofProbability != null && spoofProbability > 0.8;
    }

    /**
     * Get face quality score (0-1, higher is better)
     */
    public double getQualityScore() {
        double score = 0.0;
        int factors = 0;

        if (sharpness != null) {
            score += sharpness;
            factors++;
        }

        if (brightness != null) {
            // Optimal brightness is around 0.5
            double brightnessScore = 1.0 - Math.abs(brightness - 0.5) * 2;
            score += Math.max(0, brightnessScore);
            factors++;
        }

        if (occlusionLevel != null) {
            score += (1.0 - occlusionLevel); // Less occlusion is better
            factors++;
        }

        return factors > 0 ? score / factors : 0.0;
    }

    /**
     * Get formatted age range string
     */
    public String getAgeRange() {
        if (ageLow != null && ageHigh != null) {
            return ageLow + "-" + ageHigh;
        }
        return "Unknown";
    }

    /**
     * Get confidence level description
     */
    public String getConfidenceLevel() {
        if (spoofProbability == null)
            return "Unknown";

        if (spoofProbability < 0.2)
            return "High Confidence";
        if (spoofProbability < 0.4)
            return "Medium Confidence";
        if (spoofProbability < 0.6)
            return "Low Confidence";
        return "Very Low Confidence";
    }
}
