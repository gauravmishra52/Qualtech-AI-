package com.qualtech_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedFaceAnalysisResult {
    private List<AdvancedFaceDetail> faceDetails;
    private int totalFaces;
    
    public List<AdvancedFaceDetail> getFaceDetails() {
        return faceDetails != null ? faceDetails : new ArrayList<>();
    }
    
    /**
     * Get the highest quality face from all detected faces
     */
    public AdvancedFaceDetail getBestQualityFace() {
        return getFaceDetails().stream()
                .max((f1, f2) -> Double.compare(f1.getQualityScore(), f2.getQualityScore()))
                .orElse(null);
    }
    
    /**
     * Get faces that are likely spoofs
     */
    public List<AdvancedFaceDetail> getSuspiciousFaces() {
        return getFaceDetails().stream()
                .filter(AdvancedFaceDetail::isLikelySpoof)
                .toList();
    }
    
    /**
     * Get legitimate faces (not likely spoofs)
     */
    public List<AdvancedFaceDetail> getLegitimateFaces() {
        return getFaceDetails().stream()
                .filter(face -> !face.isLikelySpoof())
                .toList();
    }
    
    /**
     * Check if any faces are detected as spoofs
     */
    public boolean hasSpoofAttempts() {
        return !getSuspiciousFaces().isEmpty();
    }
    
    /**
     * Get overall analysis summary
     */
    public String getAnalysisSummary() {
        if (totalFaces == 0) {
            return "No faces detected";
        }
        
        int spoofCount = getSuspiciousFaces().size();
        int legitCount = getLegitimateFaces().size();
        
        if (spoofCount > 0 && legitCount > 0) {
            return String.format("Detected %d faces (%d legitimate, %d suspicious)", 
                    totalFaces, legitCount, spoofCount);
        } else if (spoofCount > 0) {
            return String.format("Detected %d suspicious faces (potential spoof attempts)", spoofCount);
        } else {
            return String.format("Detected %d legitimate faces", legitCount);
        }
    }
}
