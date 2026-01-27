package com.qualtech_ai.service;

import com.azure.ai.vision.face.FaceClient;
import com.azure.ai.vision.face.models.*;
import com.azure.core.util.BinaryData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AzureFaceService {

    private final FaceClient faceClient;

    // 1. Isolation: Single-thread executor
    private final ExecutorService azureExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "azure-face-isolation-thread");
        t.setDaemon(true);
        return t;
    });

    // 2. Concurrency Control: Semaphore(1)
    private final Semaphore azureSemaphore = new Semaphore(1);

    // 3. Hard Rate Limiting: Increased limit to reduce blocking
    private final AtomicInteger rateLimitCounter = new AtomicInteger(0);
    private final AtomicLong rateLimitWindowStart = new AtomicLong(System.currentTimeMillis());
    private static final int MAX_REQUESTS_PER_MINUTE = 200; // Increased from 60
    private static final long RATE_LIMIT_WINDOW_MS = 60000;

    // 4. Circuit Breaker with error counting
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicLong circuitResetTime = new AtomicLong(0);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private static final long CIRCUIT_COOLDOWN_MS = 10000; // 10s cooldown for better recovery
    private static final int ERROR_THRESHOLD = 3; // Trip after 3 consecutive errors

    @org.springframework.beans.factory.annotation.Value("${azure.face.enabled:false}")
    private boolean enabled;

    public AzureFaceService(Optional<FaceClient> faceClient) {
        this.faceClient = faceClient.orElse(null);
        if (this.faceClient == null) {
            log.warn("Azure Face Client is not configured. Azure features will be disabled.");
        }
    }

    /**
     * Safe, Isolated, and Rate-Limited Face Detection
     * Returns CompletableFuture<Optional<List<FaceDetectionResult>>>
     * - Optional.empty() -> Failed, Skipped, Rate Limited, or Circuit Open (Treat
     * as "No Opinion")
     * - Optional.of(list) -> Success (List might be empty if no faces found)
     */
    public CompletableFuture<Optional<List<FaceDetectionResult>>> detectFacesSafe(byte[] imageBytes) {
        if (faceClient == null || !enabled) {
            if (!enabled) {
                log.info("Azure Face API is disabled - skipping");
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            // 1. Check Circuit Breaker
            if (isCircuitOpen()) {
                log.warn("Azure Circuit Breaker is OPEN. Skipping request.");
                return Optional.empty();
            }

            // 2. Check Rate Limit
            if (!acquireRateLimitToken()) {
                log.warn("Azure Rate Limit Exceeded ({} req/min). Skipping request.", MAX_REQUESTS_PER_MINUTE);
                return Optional.empty();
            }

            // 3. Check Semaphore (prevent queuing if thread is busy)
            if (!azureSemaphore.tryAcquire()) {
                log.warn("Azure Executor is BUSY. Skipping request to prevent blocking.");
                return Optional.empty();
            }

            try {
                // 4. Validate Input
                if (imageBytes == null || imageBytes.length < 1024) {
                    log.debug("Image too small or null for Azure.");
                    return Optional.of(new ArrayList<>());
                }

                // 5. Execute Service Call
                return executeAzureCall(imageBytes);
            } finally {
                azureSemaphore.release();
            }
        }, azureExecutor);
    }

    /**
     * Legacy wrapper - Deprecated but kept for compatibility logic if needed
     */
    public List<FaceDetectionResult> detectFaces(byte[] imageBytes) {
        try {
            return detectFacesSafe(imageBytes).get(5, TimeUnit.SECONDS).orElse(new ArrayList<>());
        } catch (Exception e) {
            log.warn("Legacy detectFaces call failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private synchronized boolean acquireRateLimitToken() {
        long now = System.currentTimeMillis();
        if (now - rateLimitWindowStart.get() > RATE_LIMIT_WINDOW_MS) {
            // Reset window
            rateLimitWindowStart.set(now);
            rateLimitCounter.set(0);
        }

        if (rateLimitCounter.get() < MAX_REQUESTS_PER_MINUTE) {
            rateLimitCounter.incrementAndGet();
            return true;
        }
        return false;
    }

    private boolean isCircuitOpen() {
        if (circuitOpen.get()) {
            if (System.currentTimeMillis() > circuitResetTime.get()) {
                // Half-open: Allow one probe and reset error counter
                log.info("Azure Circuit Breaker entering HALF-OPEN state (allowing probe request)");
                consecutiveErrors.set(0); // Reset error counter for probe
                return false; // Allow one request through
            }
            return true;
        }
        return false;
    }

    private Optional<List<FaceDetectionResult>> executeAzureCall(byte[] imageBytes) {
        try {
            log.debug("Calling Azure Face API...");
            List<FaceDetectionResult> results = faceClient.detect(
                    BinaryData.fromBytes(imageBytes),
                    FaceDetectionModel.DETECTION_03,
                    FaceRecognitionModel.RECOGNITION_04,
                    true, // returnFaceId
                    null, // attributes
                    false, // landmarks
                    false, // attributes bool
                    null);

            // Success: Reset error counter and close circuit if open
            consecutiveErrors.set(0);
            if (circuitOpen.get()) {
                log.info("✅ Azure Circuit Breaker CLOSED (Successful request after failure)");
                circuitOpen.set(false);
            }

            return Optional.ofNullable(results).or(() -> Optional.of(new ArrayList<>()));

        } catch (com.azure.core.exception.HttpResponseException e) {
            log.error("Azure Face API HTTP Error [Status: {}]: {}", e.getResponse().getStatusCode(), e.getMessage());
            handleError(e.getResponse().getStatusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Azure Face API Failed: {}", e.getMessage());
            handleError(0); // Unknown error
            return Optional.empty();
        }
    }

    /**
     * Handle errors with exponential backoff for circuit breaker
     */
    private void handleError(int statusCode) {
        int errors = consecutiveErrors.incrementAndGet();

        // Only trip circuit after threshold
        if (errors >= ERROR_THRESHOLD) {
            circuitOpen.set(true);
            long cooldown = CIRCUIT_COOLDOWN_MS * (long) Math.pow(2, Math.min(errors - ERROR_THRESHOLD, 3)); // Max 80s
            circuitResetTime.set(System.currentTimeMillis() + cooldown);
            log.warn("⚠️  Azure Circuit Breaker OPENED after {} consecutive errors. Cooldown: {}ms", errors, cooldown);
        } else {
            log.warn("Azure error #{} (threshold: {}). Status: {}", errors, ERROR_THRESHOLD, statusCode);
        }
    }

    public boolean isAvailable() {
        return faceClient != null && !isCircuitOpen();
    }
}
