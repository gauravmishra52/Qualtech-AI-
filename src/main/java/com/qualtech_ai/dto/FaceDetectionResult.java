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
}
