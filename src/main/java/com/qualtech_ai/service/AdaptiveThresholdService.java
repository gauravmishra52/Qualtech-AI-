package com.qualtech_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AdaptiveThresholdService {

    @Value("${face.recognition.threshold.normal:0.8}")
    private double normalThreshold;

    @Value("${face.recognition.threshold.low-light:0.7}")
    private double lowLightThreshold;

    @Value("${face.recognition.threshold.very-low-light:0.6}")
    private double veryLowLightThreshold;

    private final ConcurrentHashMap<String, AtomicInteger> userAttemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> userAverageConfidence = new ConcurrentHashMap<>();

    public double calculateAdaptiveThreshold(Mat image, String userId) {
        double brightness = calculateImageBrightness(image);
        double adaptiveThreshold = getThresholdForLighting(brightness);

        adaptiveThreshold = adjustBasedOnUserHistory(userId, adaptiveThreshold);

        log.debug("Adaptive threshold calculation - User: {}, Brightness: {}, Threshold: {}",
                userId, brightness, adaptiveThreshold);

        return adaptiveThreshold;
    }

    private double calculateImageBrightness(Mat image) {
        try {
            Mat gray = new Mat();
            org.bytedeco.opencv.global.opencv_imgproc.cvtColor(image, gray,
                    org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY);

            org.bytedeco.opencv.opencv_core.Scalar meanScalar = org.bytedeco.opencv.global.opencv_core.mean(gray);
            double brightness = meanScalar.get(0);

            gray.release();
            return brightness;
        } catch (Exception e) {
            log.error("Error calculating image brightness for adaptive threshold: {}", e.getMessage());
            return 128.0;
        }
    }

    private double getThresholdForLighting(double brightness) {
        if (brightness < 50) {
            return veryLowLightThreshold;
        } else if (brightness < 100) {
            return lowLightThreshold;
        } else {
            return normalThreshold;
        }
    }

    private double adjustBasedOnUserHistory(String userId, double baseThreshold) {
        AtomicInteger attemptCount = userAttemptCounts.get(userId);
        Double avgConfidence = userAverageConfidence.get(userId);

        if (attemptCount != null && avgConfidence != null && attemptCount.get() > 3) {
            if (avgConfidence > 0.9) {
                return Math.max(baseThreshold - 0.05, 0.5);
            } else if (avgConfidence < 0.7) {
                return Math.min(baseThreshold + 0.05, 0.95);
            }
        }

        return baseThreshold;
    }

    public void recordVerificationAttempt(String userId, double confidence, boolean successful) {
        userAttemptCounts.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();

        userAverageConfidence.compute(userId, (k, currentAvg) -> {
            if (currentAvg == null) {
                return confidence;
            } else {
                return (currentAvg * 0.8) + (confidence * 0.2);
            }
        });

        log.debug("Recorded verification attempt - User: {}, Confidence: {}, Successful: {}",
                userId, confidence, successful);
    }

    public void resetUserHistory(String userId) {
        userAttemptCounts.remove(userId);
        userAverageConfidence.remove(userId);
        log.debug("Reset verification history for user: {}", userId);
    }

    public String getLightingCondition(double brightness) {
        if (brightness < 50) {
            return "VERY_LOW_LIGHT";
        } else if (brightness < 100) {
            return "LOW_LIGHT";
        } else if (brightness < 180) {
            return "NORMAL_LIGHT";
        } else {
            return "BRIGHT_LIGHT";
        }
    }

    public double getNormalThreshold() {
        return normalThreshold;
    }

    public double getLowLightThreshold() {
        return lowLightThreshold;
    }

    public double getVeryLowLightThreshold() {
        return veryLowLightThreshold;
    }
}
