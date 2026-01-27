package com.qualtech_ai.dto;

import com.qualtech_ai.entity.FaceUser;
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
public class FaceVerificationResponse {
    private boolean success;
    private String message;
    @Builder.Default
    private List<FaceDetectionResult> detections = new ArrayList<>();

    // For backward compatibility and single-user response contract
    private FaceUser user;
    private double confidence;
    private boolean authorized;
    private String provider;

    public static FaceVerificationResponse success(List<FaceDetectionResult> detections) {
        FaceVerificationResponse response = new FaceVerificationResponse();
        response.setSuccess(true);
        response.setMessage("Face(s) analyzed successfully");
        response.setDetections(detections);

        // Populate first detection for contract compliance
        if (!detections.isEmpty()) {
            FaceDetectionResult first = detections.get(0);
            response.setUser(first.getUser());
            response.setConfidence(first.getConfidence() / 100.0); // Convert to 0-1 range for contract
            response.setAuthorized(first.isAuthorized());
            response.setProvider(first.getProvider());
        }

        return response;
    }

    public static FaceVerificationResponse failure(String message) {
        FaceVerificationResponse response = new FaceVerificationResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setDetections(new ArrayList<>());
        return response;
    }

    public boolean hasAuthorizedUser() {
        if (detections == null || detections.isEmpty())
            return false;
        return detections.stream().anyMatch(FaceDetectionResult::isAuthorized);
    }
}
