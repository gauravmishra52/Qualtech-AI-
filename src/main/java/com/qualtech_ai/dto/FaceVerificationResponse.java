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

    // For backward compatibility
    private FaceUser user;
    private double confidence;

    public static FaceVerificationResponse success(List<FaceDetectionResult> detections) {
        FaceVerificationResponse response = new FaceVerificationResponse();
        response.setSuccess(true);
        response.setMessage("Face(s) analyzed successfully");
        response.setDetections(detections);

        // Populate first detection for backward compatibility
        if (!detections.isEmpty()) {
            FaceDetectionResult first = detections.get(0);
            response.setUser(first.getUser());
            response.setConfidence(first.getConfidence());
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
}
