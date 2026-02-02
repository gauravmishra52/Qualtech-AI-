package com.qualtech_ai.service;

import com.qualtech_ai.dto.FaceVerificationRequest;
import com.qualtech_ai.dto.FaceVerificationResponse;
import com.qualtech_ai.dto.FaceDetectionResult;
import com.qualtech_ai.entity.FaceUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MultiFrameVerificationService {

    @Value("${face.verification.multi-frame.count:3}")
    private int frameCount;

    @Value("${face.verification.multi-frame.timeout-ms:2000}")
    private long frameTimeoutMs;

    @Value("${face.verification.multi-frame.majority-threshold:0.6}")
    private double majorityThreshold;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public FaceVerificationResponse verifyWithMultipleFrames(
            List<MultipartFile> frames,
            FaceVerificationRequest baseRequest,
            FaceRecognitionService faceRecognitionService) throws IOException {

        if (frames == null || frames.isEmpty()) {
            return FaceVerificationResponse.failure("No frames provided for multi-frame verification");
        }

        if (frames.size() == 1) {
            log.debug("Single frame provided, performing standard verification");
            return faceRecognitionService.verifyFace(baseRequest);
        }

        log.info("Starting multi-frame verification with {} frames", frames.size());

        List<CompletableFuture<FaceVerificationResponse>> futures = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {
            final int frameIndex = i;
            final MultipartFile frame = frames.get(i);

            CompletableFuture<FaceVerificationResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    FaceVerificationRequest frameRequest = createFrameRequest(baseRequest, frame, frameIndex);
                    return faceRecognitionService.verifyFace(frameRequest);
                } catch (Exception e) {
                    log.error("Error processing frame {}: {}", frameIndex, e.getMessage());
                    return FaceVerificationResponse.failure("Frame processing failed: " + e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        List<FaceVerificationResponse> responses = new ArrayList<>();
        for (CompletableFuture<FaceVerificationResponse> future : futures) {
            try {
                FaceVerificationResponse response = future.get(frameTimeoutMs, TimeUnit.MILLISECONDS);
                responses.add(response);
            } catch (Exception e) {
                log.warn("Frame verification timed out or failed: {}", e.getMessage());
                responses.add(FaceVerificationResponse.failure("Frame timeout: " + e.getMessage()));
            }
        }

        return consolidateMultiFrameResults(responses);
    }

    private FaceVerificationRequest createFrameRequest(FaceVerificationRequest baseRequest, MultipartFile frame,
            int frameIndex) {
        FaceVerificationRequest frameRequest = new FaceVerificationRequest();
        frameRequest.setImage(frame);
        frameRequest.setProvider(baseRequest.getProvider());
        frameRequest.setLive(baseRequest.isLive());
        return frameRequest;
    }

    private FaceVerificationResponse consolidateMultiFrameResults(List<FaceVerificationResponse> responses) {
        if (responses.isEmpty()) {
            return FaceVerificationResponse.failure("No successful frame verifications");
        }

        Map<String, FrameVote> voteMap = new HashMap<>();
        List<FaceDetectionResult> allDetections = new ArrayList<>();
        int successfulFrames = 0;

        for (int i = 0; i < responses.size(); i++) {
            FaceVerificationResponse response = responses.get(i);

            if (response.isSuccess() && response.getDetections() != null) {
                successfulFrames++;

                for (FaceDetectionResult detection : response.getDetections()) {
                    allDetections.add(detection);

                    if (detection.isAuthorized() && detection.getUser() != null) {
                        String userId = detection.getUser().getId();
                        voteMap.compute(userId, (k, existing) -> {
                            if (existing == null) {
                                return new FrameVote(detection.getConfidence(), 1);
                            } else {
                                existing.totalConfidence += detection.getConfidence();
                                existing.voteCount++;
                                return existing;
                            }
                        });
                    }
                }
            }
        }

        if (voteMap.isEmpty()) {
            log.warn("No authorized faces detected in any frame");
            // Return success but with current detections so UI can show why it failed
            // (unknowns/spoofs)
            return FaceVerificationResponse.success(allDetections.stream().limit(5).toList());
        }

        String winningUserId = determineWinningUser(voteMap, successfulFrames);
        if (winningUserId == null) {
            return FaceVerificationResponse.failure("Insufficient majority in multi-frame verification");
        }

        FrameVote winningVote = voteMap.get(winningUserId);
        Optional<FaceUser> winningUser = allDetections.stream()
                .filter(d -> d.getUser() != null && d.getUser().getId().equals(winningUserId))
                .map(FaceDetectionResult::getUser)
                .findFirst();

        if (!winningUser.isPresent()) {
            return FaceVerificationResponse.failure("Unable to determine winning user");
        }

        // MOTION & BLINK DETECTION: Check if the WINNING face is live
        List<FaceDetectionResult> winningUserDetections = allDetections.stream()
                .filter(d -> d.getUser() != null && d.getUser().getId().equals(winningUserId))
                .toList();

        double livenessMotionScore = calculateMotionScore(winningUserDetections);
        boolean isLive = livenessMotionScore > 1.0; // Adjusted threshold for micro-movements + blink

        FaceDetectionResult consolidatedResult = createConsolidatedResult(
                winningUser.get(),
                winningVote,
                successfulFrames,
                responses.size(),
                isLive);

        // Build a list of other notable detections (e.g. spoofs or other people)
        List<FaceDetectionResult> finalResults = new ArrayList<>();
        finalResults.add(consolidatedResult);

        // Add other unique registered users consistently seen (Label others)
        Set<String> addedUserIds = new HashSet<>();
        addedUserIds.add(winningUserId);

        allDetections.stream()
                .filter(d -> d.getUser() != null && !addedUserIds.contains(d.getUser().getId()))
                .filter(d -> d.getConfidence() >= 0.75)
                .forEach(d -> {
                    if (addedUserIds.add(d.getUser().getId())) {
                        d.setAuthorized(false);
                        d.setAnalysisMessage("👤 ALSO PRESENT: " + d.getUser().getName());
                        finalResults.add(d);
                    }
                });

        // Add representative spoofs
        allDetections.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsSpoofed()))
                .findFirst()
                .ifPresent(d -> {
                    d.setAuthorized(false);
                    d.setAnalysisMessage("🚨 SPOOF REJECTED: Static/Phone screen artifact.");
                    finalResults.add(d);
                });

        // Add unknown faces
        allDetections.stream()
                .filter(d -> d.getUser() == null && !Boolean.TRUE.equals(d.getIsSpoofed()))
                .findFirst()
                .ifPresent(d -> {
                    d.setAnalysisMessage("👤 UNKNOWN: Unregistered face.");
                    finalResults.add(d);
                });

        log.info("Multi-frame verification successful - User: {}, Votes: {}/{}, Avg Confidence: {}, Motion: {}",
                winningUserId, winningVote.voteCount, successfulFrames,
                winningVote.totalConfidence / winningVote.voteCount, isLive);

        return FaceVerificationResponse.success(finalResults);
    }

    private double calculateMotionScore(List<FaceDetectionResult> detections) {
        if (detections.size() < 2)
            return 0.0;

        // 1. Position Variance (X, Y)
        double meanX = detections.stream().mapToDouble(FaceDetectionResult::getX).average().orElse(0);
        double meanY = detections.stream().mapToDouble(FaceDetectionResult::getY).average().orElse(0);
        double varX = detections.stream().mapToDouble(d -> Math.pow(d.getX() - meanX, 2)).average().orElse(0);
        double varY = detections.stream().mapToDouble(d -> Math.pow(d.getY() - meanY, 2)).average().orElse(0);
        double positionMotion = Math.sqrt(varX + varY);

        // 2. Pose Variance (Pitch, Roll, Yaw) - Harder to spoof than position
        double varPitch = calculateVariance(
                detections.stream().map(FaceDetectionResult::getPitch).filter(Objects::nonNull).toList());
        double varRoll = calculateVariance(
                detections.stream().map(FaceDetectionResult::getRoll).filter(Objects::nonNull).toList());
        double varYaw = calculateVariance(
                detections.stream().map(FaceDetectionResult::getYaw).filter(Objects::nonNull).toList());
        double poseMotion = Math.sqrt(varPitch + varYaw + varRoll);

        // 3. Blink Detection
        boolean blinkDetected = detectBlink(detections);

        log.info("Liveness Multi-Frame Analysis: PosMotion={}, PoseMotion={}, BlinkDetected={}",
                positionMotion, poseMotion, blinkDetected);

        // Return a combined score
        double score = (positionMotion * 2.0) + (poseMotion * 5.0);
        if (blinkDetected)
            score += 50.0;

        return score;
    }

    private boolean detectBlink(List<FaceDetectionResult> detections) {
        if (detections.size() < 2)
            return false;

        boolean seenOpen = false;
        boolean seenClosed = false;

        for (FaceDetectionResult d : detections) {
            if (d.getEyesOpen() != null) {
                if (d.getEyesOpen())
                    seenOpen = true;
                else
                    seenClosed = true;
            }
        }

        return seenOpen && seenClosed;
    }

    private double calculateVariance(List<Double> values) {
        if (values == null || values.size() < 2)
            return 0.0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
    }

    private String determineWinningUser(Map<String, FrameVote> voteMap, int successfulFrames) {
        return voteMap.entrySet().stream()
                .filter(entry -> (double) entry.getValue().voteCount / successfulFrames >= majorityThreshold)
                .max(Comparator.comparingDouble(entry -> entry.getValue().averageConfidence()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private FaceDetectionResult createConsolidatedResult(FaceUser user, FrameVote vote, int successfulFrames,
            int totalFrames, boolean isLive) {
        return FaceDetectionResult.builder()
                .x(0)
                .y(0)
                .width(0)
                .height(0)
                .authorized(isLive) // SECURITY: Only authorized if liveness passed
                .user(user)
                .confidence(vote.averageConfidence())
                .isLive(isLive)
                .livenessScore(isLive ? 100.0 : 40.0)
                .emotion("Multi-frame verified")
                .age("N/A")
                .moving(isLive)
                .qualityScore((double) vote.voteCount / totalFrames)
                .spoofProbability(isLive ? 0.0 : 0.8)
                .isSpoofed(!isLive)
                .confidenceLevel("HIGH")
                .analysisMessage(
                        String.format("Multi-frame verification: %d/%d frames matched. Liveness: %s",
                                vote.voteCount, totalFrames, isLive ? "PASSED" : "FAILED (No Motion/Blink)"))
                .build();
    }

    public boolean shouldUseMultiFrameVerification(FaceVerificationRequest request) {
        return request.isLive() && frameCount > 1;
    }

    public int getFrameCount() {
        return frameCount;
    }

    private static class FrameVote {
        double totalConfidence;
        int voteCount;

        FrameVote(double confidence, int voteCount) {
            this.totalConfidence = confidence;
            this.voteCount = voteCount;
        }

        double averageConfidence() {
            return voteCount > 0 ? totalConfidence / voteCount : 0.0;
        }
    }
}
