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

    private FaceVerificationRequest createFrameRequest(FaceVerificationRequest baseRequest, MultipartFile frame, int frameIndex) {
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
            return FaceVerificationResponse.failure("No authorized faces detected in multi-frame analysis");
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

        FaceDetectionResult consolidatedResult = createConsolidatedResult(
                winningUser.get(), 
                winningVote, 
                successfulFrames, 
                responses.size()
        );

        log.info("Multi-frame verification successful - User: {}, Votes: {}/{}, Avg Confidence: {:.3f}", 
                winningUserId, winningVote.voteCount, successfulFrames, winningVote.totalConfidence / winningVote.voteCount);

        return FaceVerificationResponse.success(Collections.singletonList(consolidatedResult));
    }

    private String determineWinningUser(Map<String, FrameVote> voteMap, int successfulFrames) {
        return voteMap.entrySet().stream()
                .filter(entry -> (double) entry.getValue().voteCount / successfulFrames >= majorityThreshold)
                .max(Comparator.comparingDouble(entry -> entry.getValue().averageConfidence()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private FaceDetectionResult createConsolidatedResult(FaceUser user, FrameVote vote, int successfulFrames, int totalFrames) {
        return FaceDetectionResult.builder()
                .x(0)
                .y(0)
                .width(0)
                .height(0)
                .authorized(true)
                .user(user)
                .confidence(vote.averageConfidence())
                .isLive(true)
                .livenessScore(100.0)
                .emotion("Multi-frame verified")
                .age("N/A")
                .moving(false)
                .qualityScore((double) vote.voteCount / totalFrames)
                .spoofProbability(0.0)
                .isSpoofed(false)
                .confidenceLevel("HIGH")
                .analysisMessage(String.format("Multi-frame verification: %d/%d frames matched", vote.voteCount, totalFrames))
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
