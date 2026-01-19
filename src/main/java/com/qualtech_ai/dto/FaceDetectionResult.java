package com.qualtech_ai.dto;

import com.qualtech_ai.entity.FaceUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceDetectionResult {
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean authorized;
    private FaceUser user;
    private double confidence;
    private boolean isLive;
    private double livenessScore;
    private String emotion;
    private boolean moving;
    private String age;
    
    // Enhanced fields for advanced analysis
    private Double qualityScore;
    private Double spoofProbability;
    private String confidenceLevel;
    private String analysisMessage;
    private Boolean isSpoofed; // Changed to Boolean wrapper class
    
    /**
     * Get status message for display
     */
    public String getStatusMessage() {
        if (isSpoofed != null && isSpoofed) {
            return "🚫 SPOOF DETECTED";
        }
        if (!isLive) {
            return "❌ NOT LIVE";
        }
        if (authorized && user != null) {
            return "✅ AUTHORIZED: " + user.getName().toUpperCase();
        }
        if (user != null) {
            return "⚠️ RECOGNIZED BUT NOT AUTHORIZED";
        }
        return "❌ UNAUTHORIZED";
    }
    
    /**
     * Get color code for visualization
     */
    public String getStatusColor() {
        if (isSpoofed != null && isSpoofed) {
            return "red"; // Spoof detected
        }
        if (!isLive) {
            return "orange"; // Not live
        }
        if (authorized && user != null) {
            return "green"; // Authorized
        }
        if (user != null) {
            return "yellow"; // Recognized but not authorized
        }
        return "red"; // Unauthorized
    }
    
    /**
     * Get detailed analysis text
     */
    public String getDetailedAnalysis() {
        StringBuilder sb = new StringBuilder();
        
        if (isSpoofed != null && isSpoofed) {
            sb.append("🚫 SPOOF ALERT | ");
        }
        
        if (emotion != null && age != null) {
            sb.append(emotion).append(" | Age: ").append(age).append(" | ");
        }
        
        if (livenessScore > 0) {
            sb.append(String.format("Liveness: %.1f", livenessScore));
        }
        
        if (qualityScore != null) {
            sb.append(String.format(" | Quality: %.1f%%", qualityScore * 100));
        }
        
        if (spoofProbability != null) {
            sb.append(String.format(" | Spoof Risk: %.1f%%", spoofProbability * 100));
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this result represents a high-confidence detection
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8 && isLive && (isSpoofed == null || !isSpoofed);
    }
}
