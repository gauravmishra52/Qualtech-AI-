package com.qualtech_ai.service.impl;

import com.qualtech_ai.dto.FaceRegistrationRequest;
import com.qualtech_ai.dto.FaceVerificationRequest;
import com.qualtech_ai.dto.FaceVerificationResponse;
import com.qualtech_ai.dto.FaceDetectionResult;
import com.qualtech_ai.dto.AdvancedFaceAnalysisResult;
import com.qualtech_ai.dto.AdvancedFaceDetail;
import com.qualtech_ai.dto.PreprocessedFaceData;
import com.qualtech_ai.service.S3Service;
import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.exception.ResourceNotFoundException;
import com.qualtech_ai.repository.FaceUserRepository;
import com.qualtech_ai.projection.FaceEmbeddingView;
import com.qualtech_ai.service.FaceRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.qualtech_ai.enums.FaceProvider;
import com.qualtech_ai.repository.FaceVerificationLogRepository;

import com.qualtech_ai.service.AwsFaceService;
import com.qualtech_ai.service.AzureFaceService;
import com.qualtech_ai.entity.FaceVerificationLog;
import com.qualtech_ai.util.FaceImagePreprocessor;
// AdaptiveThresholdService disabled for stabilization - using fixed threshold
// import com.qualtech_ai.service.AdaptiveThresholdService;

// SilentSelfImprovementService disabled for stabilization
// import com.qualtech_ai.service.SilentSelfImprovementService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.opencv.global.opencv_core.CV_32F;
import static org.bytedeco.opencv.global.opencv_dnn.blobFromImage;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    private static final double FACE_MATCH_THRESHOLD = 0.5; // Reduced threshold for better recognition

    private static final double FACE_DETECTION_CONFIDENCE = 0.4; // Reduced for more sensitive detection
    private static final int MAX_FACES_TO_PROCESS = 3; // Limit for real-time performance
    private static final double AUTH_SCORE_THRESHOLD = 0.7; // Weighted score threshold for authorization

    // DNN model paths - Using Caffe models
    private static final String FACE_DETECTOR_MODEL_RES = "classpath:face_models/deploy.prototxt";
    private static final String FACE_DETECTOR_WEIGHTS_RES = "classpath:face_models/res10_300x300_ssd_iter_140000.caffemodel";

    // Image preprocessing parameters - Optimized for speed
    private static final int INPUT_WIDTH = 300;
    private static final int INPUT_HEIGHT = 300;
    private static final int FEATURE_SIZE = 128; // Standard face embedding size

    // Performance optimization constants
    private static final int FACE_SIZE_THRESHOLD = 40; // Minimum face size for detection
    private static final double LIVENESS_THRESHOLD = 15.0; // Significantly reduced to prevent false negatives on webcam

    private final FaceUserRepository faceUserRepository;
    private final S3Service s3Service;
    private final ResourceLoader resourceLoader;
    private final AwsFaceService awsFaceService;
    private final AzureFaceService azureFaceService;
    private final FaceVerificationLogRepository faceVerificationLogRepository;
    private final FaceImagePreprocessor faceImagePreprocessor;
    private final FaceUserTxService faceUserTxService;
    // AdaptiveThresholdService disabled for stabilization - using fixed threshold
    // private final AdaptiveThresholdService adaptiveThresholdService;
    @Value("${face.recognition.threshold:0.85}")
    private double fixedThreshold;

    // SilentSelfImprovementService disabled for stabilization
    // private final SilentSelfImprovementService silentSelfImprovementService;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Fix for missing field

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_REQUESTS = 4;

    // DNN-based face detector - Pre-initialized for performance
    private Net faceDetector;
    private boolean initializationFailed = false;
    private volatile boolean modelLoaded = false;

    // Pre-load models on startup
    @jakarta.annotation.PostConstruct
    public void initializeModels() {
        log.info("Pre-loading face detection models for optimal performance...");
        Thread modelLoader = new Thread(() -> {
            try {
                initializeFaceDetector();
                modelLoaded = true;
                log.info("Face detection models loaded successfully - Ready for real-time detection!");
            } catch (Exception e) {
                log.error("Failed to pre-load face detection models: {}", e.getMessage());
                initializationFailed = true;
            }
        });
        modelLoader.setDaemon(true);
        modelLoader.start();
    }

    @Override
    public FaceUser registerFace(FaceRegistrationRequest request) throws IOException {
        log.info("Starting face registration for email: {}", request.getEmail());

        // 1️⃣ Preprocess (NO DB) - face detection, validation, feature extraction
        PreprocessedFaceData data = preprocess(request);

        // 2️⃣ Check email uniqueness (READ-ONLY DB)
        if (faceUserTxService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        // 3️⃣ Duplicate check (READ-ONLY DB)
        checkDuplicate(data.faceEmbedding());

        // 4️⃣ Create user (TX)
        FaceUser user = faceUserTxService.createUser(request, data.base64Image());

        // 5️⃣ Save embedding (TX)
        faceUserTxService.saveEmbedding(user.getId(), data.faceEmbedding());

        // 6️⃣ External services (NO TX)
        uploadImageToS3(user, request.getImage());
        indexFaceInAws(user, request.getImage());

        log.info("Successfully registered face for user: {} (ID: {})", user.getName(), user.getId());
        return user;
    }

    private void uploadImageToS3(FaceUser user, MultipartFile image) {
        // Upload image to S3 (if configured)
        if (s3Service != null) {
            try {
                String s3Key = s3Service.generateS3Key("faces", image.getOriginalFilename());
                String imageUrl = s3Service.uploadFile(image, s3Key);

                // Update user with S3 info using transaction service
                faceUserTxService.updateS3Info(user.getId(), imageUrl, s3Key);

                log.info("Successfully uploaded face image to S3 for user: {}", user.getName());
            } catch (Exception e) {
                log.warn("Failed to upload face image to S3, proceeding with local-only storage: {}", e.getMessage());
            }
        }
    }

    private void indexFaceInAws(FaceUser user, MultipartFile image) {
        // Index in AWS if available
        if (awsFaceService.isAvailable()) {
            try {
                // Use FaceUser.id (UUID from DB) as externalImageId
                String externalId = user.getId();

                software.amazon.awssdk.services.rekognition.model.IndexFacesResponse response = awsFaceService
                        .indexFace(image.getBytes(), externalId);

                if (response != null && !response.faceRecords().isEmpty()) {
                    String awsFaceId = response.faceRecords().get(0).face().faceId();

                    // Update user with AWS info using transaction service
                    faceUserTxService.updateAwsInfo(user.getId(), awsFaceId, externalId);

                    log.info("Successfully indexed face in AWS for user: {} (FaceId: {}, ExternalId: {})",
                            user.getName(), awsFaceId, externalId);
                }
            } catch (Exception e) {
                log.warn("Failed to index face in AWS Rekognition: {}", e.getMessage());
            }
        }
    }

    @Override
    public FaceVerificationResponse verifyFace(FaceVerificationRequest request) throws IOException {
        // MANDATORY VALIDATIONS for face verification (Service Level)

        // 1. Basic request validation
        if (request == null || request.getImage() == null || request.getImage().isEmpty()) {
            return FaceVerificationResponse.failure("Invalid request: No image provided");
        }

        // 2. Image size validation
        if (request.getImage().getSize() < 50 * 1024) {
            return FaceVerificationResponse.failure("Image too small (minimum 50KB required)");
        }

        // 3. AWS Collection availability check
        if (!awsFaceService.isAvailable()) {
            return FaceVerificationResponse.failure("AWS Rekognition service is not available");
        }

        // Single-flight lock to prevent parallel processing of the same frame/stream
        if (activeRequests.incrementAndGet() > MAX_CONCURRENT_REQUESTS) {
            activeRequests.decrementAndGet();
            log.debug("Too many concurrent verifications - skipping request");
            return FaceVerificationResponse.failure("Verification in progress");
        }

        Mat image = null;
        File tempFile = null;

        try {
            byte[] imageBytes = request.getImage().getBytes();

            // Prepare temporary file for OpenCV
            String originalName = request.getImage().getOriginalFilename();
            String extension = ".jpg";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            tempFile = Files.createTempFile("verify_" + UUID.randomUUID() + "_", extension).toFile();

            try (var is = new java.io.ByteArrayInputStream(imageBytes)) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IOException("Could not load image for verification");
            }

            // 1. Local Pre-Validation (Optional Fast Check)
            boolean localLivenessPassed = true;
            double localLivenessScore = 0.0;
            try {
                List<Rect> localFaces = detectFacesOptimized(image);
                if (!localFaces.isEmpty()) {
                    Rect largest = localFaces.get(0);
                    Mat faceRoi = new Mat(image, largest);
                    Mat resizedLiveness = new Mat();
                    opencv_imgproc.resize(faceRoi, resizedLiveness, new Size(160, 160));
                    localLivenessScore = calculateLivenessFast(resizedLiveness);
                    localLivenessPassed = localLivenessScore > LIVENESS_THRESHOLD;
                    resizedLiveness.release();
                    faceRoi.release();
                }
            } catch (Exception e) {
                log.error("Local preprocessing error: {}", e.getMessage());
            }

            // 2. Cloud Providers (Async)
            CompletableFuture<software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse> awsFuture = CompletableFuture
                    .supplyAsync(() -> awsFaceService.searchFace(imageBytes));

            // Azure service disabled for stabilization
            // CompletableFuture<Optional<List<com.azure.ai.vision.face.models.FaceDetectionResult>>>
            // azureFuture = azureFaceService
            // .detectFacesSafe(imageBytes);

            // Wait for AWS (Primary)
            software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse awsResponse = null;
            try {
                awsResponse = awsFuture.get(8, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("AWS Service call failed or timed out: {}", e.getMessage());
            }

            boolean awsAvailable = awsFaceService.isAvailable();

            if (!awsAvailable) {
                log.error("❌ AWS Service Unavailable");
                return FaceVerificationResponse.failure("AWS Service Unavailable");
            }
            if (awsResponse == null || awsResponse.faceMatches() == null || awsResponse.faceMatches().isEmpty()) {
                log.warn("⚠️  AUTHORIZATION DENIED: User NOT recognized");
                return FaceVerificationResponse.failure("User NOT recognized");
            }

            // AWS is available and match found - ACCEPT with additional validations
            var match = awsResponse.faceMatches().get(0);
            double awsConfidence = match.similarity();
            String externalId = match.face().externalImageId();
            String awsFaceId = match.face().faceId();

            // 4. Match confidence ≥ threshold validation
            if (awsConfidence < 80.0) {
                log.warn("⚠️  AUTHORIZATION DENIED: Low confidence {:.2f}%", awsConfidence);
                return FaceVerificationResponse
                        .failure(String.format("Low confidence: %.2f%% (minimum 80%% required)", awsConfidence));
            }

            // MANDATORY CHECK: Validate externalImageId is not null
            if (externalId == null) {
                log.error("❌ CORRUPTED AWS FACE DATA: externalImageId is null for FaceId: {}", awsFaceId);
                return FaceVerificationResponse.failure("Corrupted AWS face data - externalImageId is null");
            }

            // 4. DB Lookup - Use externalImageId to fetch DB user (GOLDEN RULE)
            log.info("🔍 Searching DB for externalId: {}", externalId);
            FaceUser matchedUser = faceUserRepository.findById(externalId).orElse(null);

            // Fallback: Try legacy externalImageId field for backward compatibility
            if (matchedUser == null) {
                log.debug("Primary ID lookup failed, trying externalImageId field: {}", externalId);
                matchedUser = faceUserRepository.findByExternalImageId(externalId).orElse(null);
            }

            // Final fallback: Try aws_face_id (Amazon's internal ID)
            if (matchedUser == null) {
                log.warn("⚠️  All ID lookups failed, trying aws_face_id: {}", awsFaceId);
                matchedUser = faceUserRepository.findByAwsFaceId(awsFaceId).orElse(null);
            }

            if (matchedUser == null) {
                log.error("❌ SYNC ERROR: Face recognized by AWS (ExternalId: {}) but NOT FOUND in DB!", externalId);
                // Fail-safe: Authorize with warning if confidence is extremely high?
                // Or just fail as per normal security. The prompt says "Do NOT deny access" for
                // sync error.
                // "Return AUTHORIZED_WITH_WARNING"
                return FaceVerificationResponse.builder()
                        .success(true)
                        .authorized(true)
                        .message("AUTHORIZED_WITH_WARNING: Recognized but profile sync issue")
                        .confidence(awsConfidence / 100.0)
                        .provider("AWS")
                        .build();
            }

            // 5. FaceUser.isActive validation
            if (!matchedUser.isActive()) {
                log.warn("⚠️  AUTHORIZATION DENIED: User {} is INACTIVE", matchedUser.getEmail());
                return FaceVerificationResponse.failure("User account is inactive");
            }

            // 6. Final Decision (confidence already validated above)
            boolean authorized = true; // Already passed confidence check
            String analysisMsg = "Authorized via AWS Rekognition";

            // Azure enhancement disabled for stabilization
            // if (azureResultOpt.isPresent() && !azureResultOpt.get().isEmpty()) {
            // analysisMsg += " + Azure confirmed presence";
            // }

            log.info("⚖️  FINAL DECISION: {} (AWS Confidence: {:.2f}%)", authorized ? "AUTHORIZED" : "NOT AUTHORIZED",
                    awsConfidence);

            // Log Verification
            logVerification(matchedUser.getId(), FaceProvider.AWS, authorized, awsConfidence, "Live", "N/A",
                    localLivenessPassed);

            // Construct Response
            FaceDetectionResult result = FaceDetectionResult.builder()
                    .user(matchedUser)
                    .authorized(authorized)
                    .confidence(awsConfidence)
                    .isLive(localLivenessPassed)
                    .livenessScore(localLivenessScore)
                    .analysisMessage(analysisMsg)
                    .provider("AWS")
                    .build();

            // Task: Include bounding box coordinates if local detection found them
            try {
                List<Rect> localFaces = detectFacesOptimized(image);
                if (!localFaces.isEmpty()) {
                    Rect box = localFaces.get(0);
                    result.setX(box.x());
                    result.setY(box.y());
                    result.setWidth(box.width());
                    result.setHeight(box.height());
                }
            } catch (Exception e) {
                log.warn("Failed to attach local box to verification result: {}", e.getMessage());
            }

            return FaceVerificationResponse.success(java.util.Collections.singletonList(result));

        } catch (Exception e) {
            log.error("Error during verification: {}", e.getMessage());
            return FaceVerificationResponse.failure("Face analysis failed: " + e.getMessage());
        } finally {
            activeRequests.decrementAndGet();
            if (image != null)
                image.release();
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }
    }

    @Override
    public List<FaceUser> getAllFaceUsers() {
        return faceUserRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteFaceUser(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        FaceUser user = faceUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Delete the image from S3 (if it exists)
        if (user.getS3Key() != null && s3Service != null) {
            try {
                s3Service.deleteFile(user.getS3Key());
            } catch (Exception e) {
                log.warn("Failed to delete face image from S3: {}", e.getMessage());
            }
        }

        // Delete the user from the database
        faceUserRepository.delete(user);
    }

    @Override
    public byte[] processImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return new byte[0];
        }

        Mat image = null;
        File tempFile = null;
        try {
            tempFile = new File(System.getProperty("java.io.tmpdir"), "process_" + System.currentTimeMillis() + ".jpg");
            try (var is = imageFile.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath());
            if (image == null || image.empty()) {
                return imageFile.getBytes();
            }

            // Detect faces to verify system is working
            if (faceDetector == null && !initializationFailed) {
                initializeFaceDetector();
            }
            if (initializationFailed) {
                log.warn("Skipping local face detection for image processing due to initialization failure.");
                return imageFile.getBytes(); // Return original image if detector failed
            }

            List<Rect> faceRects = detectFaces(image);

            // Draw boxes just for debug/verification
            for (Rect rect : faceRects) {
                opencv_imgproc.rectangle(image, rect, new Scalar(0, 255, 0, 0), 2, 8, 0);
            }

            byte[] result;
            try (BytePointer ptr = new BytePointer()) {
                opencv_imgcodecs.imencode(".jpg", image, ptr);
                result = new byte[(int) ptr.limit()];
                ptr.get(result);
            }
            return result;
        } finally {
            if (image != null)
                image.release();
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }
    }

    /**
     * Extract face features from an uploaded image using DNN-based face detection
     */
    private String extractFaceFeatures(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be null or empty");
        }

        Mat image = null;
        Mat faceRoi = null;

        File tempFile = null;

        try {
            // Create a temporary file using secure createTempFile and UUID
            String originalName = imageFile.getOriginalFilename();
            String extension = ".jpg";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            tempFile = Files.createTempFile("face_" + UUID.randomUUID() + "_", extension).toFile();

            try (var is = imageFile.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Read the image using OpenCV
            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), opencv_imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IOException("Could not load image or image is empty");
            }

            // Initialize face detector if needed
            if (faceDetector == null && !initializationFailed) {
                initializeFaceDetector();
            }
            if (initializationFailed) {
                throw new IOException("Face detection service is not available due to initialization failure.");
            }

            // Detect faces in the image
            List<Rect> faceRects = detectFaces(image);
            if (faceRects.isEmpty()) {
                throw new IOException("No faces detected in the image");
            }

            // For now, continue using the first (largest) face for
            // registration/verification
            Rect faceRect = faceRects.get(0);

            // Extract face region
            faceRoi = new Mat(image, faceRect);

            // Extract features from the face region using the helper method
            return extractFeaturesFromMat(faceRoi);

        } catch (Exception e) {
            log.error("Error extracting face features: {}", e.getMessage(), e);
            throw new IOException("Failed to extract face features: " + e.getMessage(), e);
        } finally {
            // Release all OpenCV resources
            if (image != null) {
                image.release();
            }
            if (faceRoi != null) {
                faceRoi.release();
            }

            // Clean up temporary file
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * Initialize the DNN-based face detector
     */
    private synchronized void initializeFaceDetector() {
        if (faceDetector != null || initializationFailed)
            return;

        try {
            log.info("START: initializeFaceDetector from classpath");
            System.err.println("--- NATIVE AI INITIALIZATION START ---");

            // Create temp files for models since opencv needs file paths
            Path tempDir = Files.createTempDirectory("qualtech_models");
            log.info("Using temp directory for models: {}", tempDir.toAbsolutePath());

            Path protoPath = tempDir.resolve("deploy.prototxt");
            Resource protoRes = resourceLoader.getResource(FACE_DETECTOR_MODEL_RES);
            try (var is = protoRes.getInputStream()) {
                Files.copy(is, protoPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path weightsPath = tempDir.resolve("res10_300x300_ssd_iter_140000.caffemodel");
            Resource weightsRes = resourceLoader.getResource(FACE_DETECTOR_WEIGHTS_RES);
            try (var is = weightsRes.getInputStream()) {
                Files.copy(is, weightsPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Load the network using file paths
            log.info("Calling native OpenCV readNetFromCaffe...");
            faceDetector = opencv_dnn.readNetFromCaffe(
                    protoPath.toAbsolutePath().toString(),
                    weightsPath.toAbsolutePath().toString());

            System.err.println("--- NATIVE AI INITIALIZATION SUCCESS ---");
            log.info("SUCCESS: Loaded face detection model");
        } catch (Throwable t) {
            initializationFailed = true;
            log.error("CRITICAL: Failed to initialize face detector: {}", t.getMessage());
            // Don't throw - allow app to run, just won't detect faces locally
        }
    }

    /**
     * Optimized face detection for real-time performance
     */
    private List<Rect> detectFacesOptimized(Mat image) {
        List<Rect> detectionsList = new java.util.ArrayList<>();
        Mat blob = null;
        Mat detections = null;

        try {
            // Prepare input blob with optimized parameters
            blob = blobFromImage(image, 1.0, new Size(INPUT_WIDTH, INPUT_HEIGHT),
                    new Scalar(104.0, 177.0, 123.0, 0.0), false, false, CV_32F);

            // Thread-safe access to native DNN resource
            synchronized (this) {
                if (faceDetector == null) {
                    log.warn("Face detector is null in detectFacesOptimized, attempting re-initialization.");
                    initializeFaceDetector();
                }
                if (faceDetector == null || initializationFailed) {
                    log.error("Face detector is not initialized or failed to initialize. Cannot detect faces.");
                    return detectionsList;
                }
                faceDetector.setInput(blob);
                detections = faceDetector.forward();
            }

            long[] sizes = detections.createIndexer().sizes();
            for (int i = 0; i < sizes[2]; i++) {
                try (FloatPointer data = new FloatPointer(detections.ptr(0, 0, i))) {
                    float confidence = data.get(2);

                    if (confidence > FACE_DETECTION_CONFIDENCE) {
                        int x = (int) (data.get(3) * image.cols());
                        int y = (int) (data.get(4) * image.rows());
                        int width = (int) (data.get(5) * image.cols() - x);
                        int height = (int) (data.get(6) * image.rows() - y);

                        // Optimized bound checking
                        x = Math.max(0, x);
                        y = Math.max(0, y);
                        width = Math.min(width, image.cols() - x);
                        height = Math.min(height, image.rows() - y);

                        // Reduced size threshold for better detection
                        if (width > FACE_SIZE_THRESHOLD && height > FACE_SIZE_THRESHOLD) {
                            detectionsList.add(new Rect(x, y, width, height));
                        }
                    }
                }
            }

            // Sort by area (width * height) descending - optimized
            detectionsList.sort((r1, r2) -> Integer.compare(r2.width() * r2.height(), r1.width() * r1.height()));

            return detectionsList;
        } catch (Exception e) {
            log.error("Error during optimized face detection: {}", e.getMessage());
            return detectionsList;
        } finally {
            if (detections != null)
                detections.release();
            if (blob != null)
                blob.release();
        }
    }

    /**
     * Fast liveness detection for real-time performance
     */
    private double calculateLivenessFast(Mat face) {
        Mat gray = new Mat();
        Mat laplacian = new Mat();

        try {
            // Simplified liveness detection - focus on blur detection only
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);

            Mat mean = new Mat();
            Mat stddev = new Mat();
            opencv_core.meanStdDev(laplacian, mean, stddev);
            double textureScore = Math.pow(stddev.createIndexer().getDouble(0), 2);

            return textureScore;
        } catch (Exception e) {
            log.error("Error in fast liveness calculation: {}", e.getMessage());
            return 0.0;
        } finally {
            gray.release();
            laplacian.release();
        }
    }

    /**
     * Fast emotion detection for real-time performance
     */
    private String detectEmotionFast(Mat face) {
        Mat gray = new Mat();
        try {
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);

            // Simplified emotion detection based on intensity
            Mat mean = new Mat();
            Mat stddev = new Mat();
            opencv_core.meanStdDev(gray, mean, stddev);

            double avgIntensity = mean.createIndexer().getDouble(0);
            double variance = Math.pow(stddev.createIndexer().getDouble(0), 2);

            // Simplified emotion logic
            if (variance > 2500)
                return "Expressive";
            if (avgIntensity > 180)
                return "Surprised";
            if (avgIntensity < 80)
                return "Serious";
            return "Neutral";
        } catch (Exception e) {
            return "Neutral";
        } finally {
            gray.release();
        }
    }

    /**
     * Detect all faces in the image using DNN
     * Returns a list of Rects sorted by area (largest first)
     */
    private List<Rect> detectFaces(Mat image) {
        List<Rect> detectionsList = new java.util.ArrayList<>();
        Mat blob = null;
        Mat detections = null;

        try {
            // Prepare input blob
            blob = blobFromImage(image, 1.0, new Size(INPUT_WIDTH, INPUT_HEIGHT),
                    new Scalar(104.0, 177.0, 123.0, 0.0), false, false, CV_32F);

            // Thread-safe access to native DNN resource
            synchronized (this) {
                if (faceDetector == null) { // Should not happen if initializationFailed check is done upstream
                    log.warn("Face detector is null in detectFaces, attempting re-initialization.");
                    initializeFaceDetector();
                }
                if (faceDetector == null || initializationFailed) {
                    log.error("Face detector is not initialized or failed to initialize. Cannot detect faces.");
                    return detectionsList;
                }
                faceDetector.setInput(blob);
                detections = faceDetector.forward();
            }

            long[] sizes = detections.createIndexer().sizes();
            for (int i = 0; i < sizes[2]; i++) {
                try (FloatPointer data = new FloatPointer(detections.ptr(0, 0, i))) {
                    float confidence = data.get(2);

                    if (confidence > FACE_DETECTION_CONFIDENCE) {
                        int x = (int) (data.get(3) * image.cols());
                        int y = (int) (data.get(4) * image.rows());
                        int width = (int) (data.get(5) * image.cols() - x);
                        int height = (int) (data.get(6) * image.rows() - y);

                        // Bound checking
                        x = Math.max(0, x);
                        y = Math.max(0, y);
                        width = Math.min(width, image.cols() - x);
                        height = Math.min(height, image.rows() - y);

                        if (width > 50 && height > 50) { // Filter out tiny detections
                            detectionsList.add(new Rect(x, y, width, height));
                        }
                    }
                }
            }

            // Sort by area (width * height) descending
            detectionsList.sort((r1, r2) -> Integer.compare(r2.width() * r2.height(), r1.width() * r1.height()));

            return detectionsList;
        } catch (Exception e) {
            log.error("CRITICAL: Error during native face detection: {}", e.getMessage(), e);
            return detectionsList; // Return what we found or empty
        } finally {
            if (detections != null)
                detections.release();
            if (blob != null)
                blob.release();
        }
    }

    /**
     * Extract feature vector from face image
     * This is a simplified version - in production, use a proper face recognition
     * model
     */
    private float[] extractFeatureVector(Mat face) {
        // Robust feature extraction: Divide image into grid and compute multiple
        // statistics
        // (Mean, Variance, and simple Local Symmetry)

        Mat gray = new Mat();
        opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // Equalize histogram to handle lighting variations
        Mat equalized = new Mat();
        opencv_imgproc.equalizeHist(gray, equalized);

        Mat normalized = new Mat();
        opencv_core.normalize(equalized, normalized, 0, 1.0, opencv_core.NORM_MINMAX, CV_32F, null);

        float[] features = new float[FEATURE_SIZE];
        int divisions = (int) Math.sqrt(FEATURE_SIZE / 2); // Split budget between mean and variance
        int cellW = normalized.cols() / divisions;
        int cellH = normalized.rows() / divisions;
        int idx = 0;

        for (int i = 0; i < divisions && idx < FEATURE_SIZE - 2; i++) {
            for (int j = 0; j < divisions && idx < FEATURE_SIZE - 2; j++) {
                int x = j * cellW;
                int y = i * cellH;
                int w = Math.min(cellW, normalized.cols() - x);
                int h = Math.min(cellH, normalized.rows() - y);

                Mat cell = new Mat(normalized, new Rect(x, y, w, h));
                Mat mean = new Mat();
                Mat stddev = new Mat();
                opencv_core.meanStdDev(cell, mean, stddev);

                features[idx++] = (float) mean.createIndexer().getDouble(0);
                features[idx++] = (float) stddev.createIndexer().getDouble(0);

                cell.release();
            }
        }

        // Add more global characteristics to fill the rest of the vector
        Scalar globalMean = opencv_core.mean(normalized);
        while (idx < FEATURE_SIZE) {
            features[idx++] = (float) globalMean.get(0);
        }

        gray.release();
        equalized.release();
        normalized.release();

        return features;
    }

    /**
     * Compare two face embeddings using cosine similarity
     * Returns a similarity score between 0 and 1 (higher is more similar)
     */
    private double compareFaceEmbeddings(String embeddingEmbeddingOrJson, String embedding2) {
        // Task 5: Multi-Embedding Matching
        // Identify if embedding2 is a JSON list or single string
        try {
            List<String> storedEmbeddings = new ArrayList<>();
            if (embedding2.trim().startsWith("[")) {
                storedEmbeddings = objectMapper.readValue(embedding2, new TypeReference<List<String>>() {
                });
            } else {
                storedEmbeddings.add(embedding2);
            }

            double maxScore = 0.0;
            float[] features1 = stringToFeatures(embeddingEmbeddingOrJson);

            for (String stored : storedEmbeddings) {
                float[] features2 = stringToFeatures(stored);
                double score = cosineSimilarity(features1, features2);
                if (score > maxScore)
                    maxScore = score;
            }
            return maxScore;
        } catch (Exception e) {
            log.error("Error comparing embeddings: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculate cosine similarity between two feature vectors
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            log.warn("Feature vectors have different lengths: {} vs {}", vec1.length, vec2.length);
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Convert feature vector to Base64 encoded string
     */
    /**
     * Extract features from a Mat object for self-improvement
     */
    private String extractFeaturesFromMat(Mat faceMat) throws IOException {
        if (faceMat == null || faceMat.empty()) {
            throw new IOException("Face matrix is null or empty");
        }

        Mat resizedFace = null;
        try {
            // Resize face to standard size for feature extraction
            resizedFace = new Mat();
            opencv_imgproc.resize(faceMat, resizedFace, new Size(160, 160));

            // Extract features from the face
            float[] features = extractFeatureVector(resizedFace);

            // Convert features to Base64 string for storage
            return featuresToString(features);

        } finally {
            if (resizedFace != null) {
                resizedFace.release();
            }
        }
    }

    private String featuresToString(float[] features) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < features.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(features[i]);
        }
        return java.util.Base64.getEncoder().encodeToString(sb.toString().getBytes());
    }

    /**
     * Convert Base64 encoded string back to feature vector
     */

    /**
     * Calculate liveness using Laplacian Variance (Blur detection)
     */
    private double calculateLiveness(Mat face) {
        // Enhanced liveness detection: Multiple anti-spoofing techniques
        Mat gray = new Mat();
        Mat laplacian = new Mat();

        Mat hsv = new Mat();
        Mat edges = new Mat();
        try {
            // 1. Texture analysis (Blur detection) - photos/screens are often blurry
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);
            Mat mean = new Mat();
            Mat stddev = new Mat();
            opencv_core.meanStdDev(laplacian, mean, stddev);
            double textureScore = Math.pow(stddev.createIndexer().getDouble(0), 2);

            // 2. Edge detection - photos have fewer natural edges than real faces
            opencv_imgproc.Canny(gray, edges, 50, 150);
            double edgeDensity = (double) opencv_core.countNonZero(edges) / (face.rows() * face.cols());

            // 3. Skin Color Check (HSV range for human skin)
            opencv_imgproc.cvtColor(face, hsv, opencv_imgproc.COLOR_BGR2HSV);
            Mat skinMask = new Mat();
            // Typical human skin HSV range: H: 0-20, S: 30-150, V: 60-255
            opencv_core.inRange(hsv,
                    new Mat(new Scalar(0, 30, 60, 0)),
                    new Mat(new Scalar(20, 150, 255, 0)),
                    skinMask);

            double skinPixelRatio = (double) opencv_core.countNonZero(skinMask) / (face.rows() * face.cols());
            skinMask.release();

            // 4. Reflection detection - screens have reflections
            Mat reflectionMask = new Mat();
            opencv_core.inRange(hsv,
                    new Mat(new Scalar(0, 0, 200, 0)), // Very bright values indicate reflections
                    new Mat(new Scalar(180, 50, 255, 0)),
                    reflectionMask);
            double reflectionRatio = (double) opencv_core.countNonZero(reflectionMask) / (face.rows() * face.cols());
            reflectionMask.release();

            // Combined scoring: High texture + good skin ratio + moderate edges + low
            // reflections = live person
            double combinedScore = textureScore * (0.6 + skinPixelRatio * 0.3) * (1.0 + edgeDensity * 0.1);

            // Penalize for reflections (indicates screen), but be less aggressive
            if (reflectionRatio > 0.25) {
                combinedScore *= 0.8; // Reduced penalty magnitude
            }

            log.info(
                    "Enhanced liveness analysis: Texture={:.2f}, SkinRatio={:.2f}, EdgeDensity={:.2f}, ReflectionRatio={:.2f}, Final={:.2f}, Threshold={}",
                    textureScore, skinPixelRatio, edgeDensity, reflectionRatio, combinedScore, LIVENESS_THRESHOLD);

            return combinedScore;
        } catch (Exception e) {
            log.error("Error calculating liveness: {}", e.getMessage());
            return 0.0;
        } finally {
            gray.release();
            laplacian.release();
            hsv.release();
            edges.release();
        }
    }

    /**
     * Detect emotion based on basic facial features (pixel intensity distribution)
     */
    private String detectEmotion(Mat face) {
        // Improved emotion detection: Analyze intensity distribution and variance
        Mat gray = new Mat();

        try {
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);

            // Calculate global intensity and variance
            Mat mean = new Mat();
            Mat stddev = new Mat();
            opencv_core.meanStdDev(gray, mean, stddev);

            double avgIntensity = mean.createIndexer().getDouble(0);
            double variance = Math.pow(stddev.createIndexer().getDouble(0), 2);

            // Analyze mouth region (lower 3rd of face ROI)
            int mouthY = (int) (gray.rows() * 0.7);
            int mouthH = gray.rows() - mouthY;
            Mat mouthRegion = new Mat(gray, new Rect(0, mouthY, gray.cols(), mouthH));
            Scalar mouthMean = opencv_core.mean(mouthRegion);
            double mouthIntensity = mouthMean.get(0);
            mouthRegion.release();

            // Heuristic based on research on facial expressions and intensity distribution
            if (variance > 3000)
                return "Expressive";
            if (avgIntensity > 200)
                return "Surprised";
            if (mouthIntensity > avgIntensity * 1.2)
                return "Happy"; // Brighter mouth area often indicates teeth/smile
            if (avgIntensity < 70)
                return "Serious";

            return "Neutral";
        } catch (Exception e) {
            return "Neutral";
        } finally {
            gray.release();
        }
    }

    private float[] stringToFeatures(String encoded) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(encoded));
            String[] parts = decoded.split(",");
            float[] features = new float[parts.length];

            for (int i = 0; i < parts.length; i++) {
                features[i] = Float.parseFloat(parts[i]);
            }

            return features;
        } catch (Exception e) {
            log.error("Error decoding features: {}", e.getMessage());
            return new float[FEATURE_SIZE];
        }
    }

    private FaceVerificationResponse verifyFaceAws(FaceVerificationRequest request, byte[] imageBytes, Mat fullImage,
            int imgWidth,
            int imgHeight)
            throws IOException {
        Mat preprocessedImage = null;

        try {
            log.info("Starting advanced AWS face analysis with preprocessing and adaptive threshold");

            // 1. Preprocess full image WITHOUT resizing (preserving detail for detection)
            // Resizing (as done previously) destroys small faces in group shots
            preprocessedImage = faceImagePreprocessor.preprocessFullImage(fullImage);
            byte[] preprocessedBytes = faceImagePreprocessor.matToByteArray(preprocessedImage);

            // Adaptive threshold disabled for stabilization - using fixed threshold
            // String lightingCondition = adaptiveThresholdService.getLightingCondition(
            // faceImagePreprocessor.calculateBrightness(preprocessedImage));
            String lightingCondition = "FIXED_THRESHOLD";
            log.info("Lighting condition detected: {} (adaptive threshold disabled)", lightingCondition);

            // 3. Perform advanced face analysis on the full image (DetectFaces)
            AdvancedFaceAnalysisResult advancedAnalysis = awsFaceService.analyzeFaceAdvanced(preprocessedBytes);
            if (advancedAnalysis == null) {
                return FaceVerificationResponse.failure("AWS advanced face analysis failed");
            }

            List<FaceDetectionResult> results = new ArrayList<>();

            // 4. Process EACH detected face individually for "Full Face Authentication"
            for (AdvancedFaceDetail advancedFace : advancedAnalysis.getFaceDetails()) {
                software.amazon.awssdk.services.rekognition.model.BoundingBox box = advancedFace.getBoundingBox();

                int x = (int) (box.left() * imgWidth);
                int y = (int) (box.top() * imgHeight);
                int w = (int) (box.width() * imgWidth);
                int h = (int) (box.height() * imgHeight);

                // Safety clamp
                x = Math.max(0, x);
                y = Math.max(0, y);
                w = Math.min(w, imgWidth - x);
                h = Math.min(h, imgHeight - y);

                // Get emotion from advanced analysis
                String emotion = advancedFace.getTopEmotion() != null
                        ? advancedFace.getTopEmotion().substring(0, 1).toUpperCase() +
                                advancedFace.getTopEmotion().substring(1).toLowerCase()
                        : "Neutral";

                // Get age from advanced analysis
                String age = advancedFace.getAgeRange();

                // Detailed Analysis variables
                double livenessScore = 0.0;
                boolean isLive = true;
                boolean authorized = false;
                double confidence = 0.0;
                String userId = null;
                FaceUser matchedUser = null;

                // Enhanced spoof detection
                boolean isSpoofed = advancedFace.isLikelySpoof();
                double spoofProbability = advancedFace.getSpoofProbability();
                double qualityScore = advancedFace.getQualityScore();

                Mat faceCrop = null;
                Mat faceRoiForLiveness = null;

                try {
                    if (w > 10 && h > 10) {
                        // Create a specific crop for this face from the preprocessed image
                        faceCrop = new Mat(preprocessedImage, new Rect(x, y, w, h));

                        // A. Local Liveness Check on the crop
                        livenessScore = calculateLiveness(faceCrop);
                        isLive = livenessScore > LIVENESS_THRESHOLD;

                        // B. Identity Verification: Search THIS specific face crop in AWS Collection
                        // reliable way to identify every face in the frame
                        byte[] cropBytes = faceImagePreprocessor.matToByteArray(faceCrop);

                        software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse searchResponse = null;
                        try {
                            searchResponse = awsFaceService.searchFace(cropBytes);
                        } catch (Exception e) {
                            log.debug("AWS Search failed for crop: {}", e.getMessage());
                        }

                        if (searchResponse != null && !searchResponse.faceMatches().isEmpty()) {
                            software.amazon.awssdk.services.rekognition.model.FaceMatch match = searchResponse
                                    .faceMatches().get(0);
                            confidence = match.similarity() / 100.0;

                            // Get user ID
                            String externalId = match.face().externalImageId();
                            String awsFaceId = match.face().faceId();

                            if (externalId != null) {
                                userId = externalId;
                                // Robust lookup: Try externalImageId, then primary id, then awsFaceId
                                // Using methods that avoid loading LOB data to prevent stream access issues
                                matchedUser = faceUserRepository.findByExternalImageIdWithoutImageData(externalId)
                                        .orElseGet(() -> faceUserRepository.findById(externalId)
                                                .orElseGet(() -> faceUserRepository
                                                        .findByAwsFaceIdWithoutImageData(awsFaceId)
                                                        .orElse(null)));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing face region: {}", e.getMessage());
                } finally {
                    if (faceCrop != null)
                        faceCrop.release();
                    if (faceRoiForLiveness != null)
                        faceRoiForLiveness.release();
                }

                // C. Authorization Decision (Unified Weighted Score)
                if (matchedUser != null) {
                    // Adaptive threshold disabled for stabilization - using fixed threshold
                    // double adaptiveThreshold =
                    // adaptiveThresholdService.calculateAdaptiveThreshold(
                    // preprocessedImage, userId);

                    // 1. Identity Score (60% weight) - Logarithmic scaling if needed, but linear is
                    // fine for now
                    // Ensure confidence is 0.0-1.0
                    double identityScore = confidence * 0.6;

                    // 2. Liveness Score (30% weight)
                    double livenessComponent = isLive ? 0.3 : 0.0;

                    // 3. Quality Score (10% weight)
                    double qualityComponent = qualityScore * 0.1;

                    // Calculate Base Score
                    double totalScore = identityScore + livenessComponent + qualityComponent;

                    // 4. Penalties
                    // Spoof Penalty (instead of hard reject)
                    if (isSpoofed) {
                        double penalty = 0.2; // 20% penalty
                        if (spoofProbability > 0.8)
                            penalty = 0.4; // Higher penalty for high probability
                        totalScore -= penalty;
                        log.warn("Spoof detected for user {}, applying penalty: -{}", userId, penalty);
                    }

                    // Lighting Penalty (Reflections, etc) used in liveness, but redundant here if
                    // liveness is false.

                    // Final Decision
                    authorized = totalScore >= AUTH_SCORE_THRESHOLD;

                    log.info(
                            "Auth Decision - User: {}, TotalScore: {:.3f} (Threshold: {}), Breakdown: [Id: {:.2f}, Live: {:.2f}, Qual: {:.2f}, SpoofPenalty: {}] -> Authorized: {}",
                            userId, totalScore, AUTH_SCORE_THRESHOLD, identityScore, livenessComponent,
                            qualityComponent, isSpoofed, authorized);

                    // Adaptive threshold disabled for stabilization
                    // adaptiveThresholdService.recordVerificationAttempt(userId, confidence,
                    // authorized);

                    // Silent self-improvement disabled for stabilization
                    // if (authorized && confidence >
                    // silentSelfImprovementService.getHighConfidenceThreshold()) {
                    // // Skipping complex alignment here for now
                    // }
                }

                // Build comprehensive detection result
                FaceDetectionResult result = FaceDetectionResult.builder()
                        .x(x).y(y).width(w).height(h)
                        .authorized(authorized)
                        .user(matchedUser)
                        .confidence(confidence)
                        .isLive(isLive)
                        .livenessScore(livenessScore)
                        .emotion(emotion)
                        .age(age)
                        .qualityScore(qualityScore)
                        .spoofProbability(spoofProbability)
                        .isSpoofed(isSpoofed)
                        .confidenceLevel(advancedFace.getConfidenceLevel())
                        .analysisMessage(getAnalysisMessage(advancedFace, authorized, isLive))
                        .build();

                results.add(result);

                // Logging
                if (!request.isLive()) {
                    logVerification(
                            authorized && matchedUser != null ? matchedUser.getId() : null,
                            FaceProvider.AWS,
                            authorized,
                            confidence,
                            emotion,
                            age,
                            isLive);
                }
            }

            if (results.isEmpty()) {
                return FaceVerificationResponse.failure("No faces detected by AWS");
            }

            // Provide detailed feedback about spoof attempts from the group
            if (advancedAnalysis.hasSpoofAttempts()) {
                log.warn("Potential spoofing detected in frame: {}", advancedAnalysis.getAnalysisSummary());
            }

            return FaceVerificationResponse.success(results);

        } catch (Exception e) {
            log.error("AWS Advanced Verification Failed: {}", e.getMessage());
            return FaceVerificationResponse.failure("AWS Error: " + e.getMessage());
        } finally {
            if (preprocessedImage != null) {
                preprocessedImage.release();
            }
        }
    }

    private FaceVerificationResponse verifyFaceAzure(FaceVerificationRequest request, byte[] imageBytes, Mat fullImage)
            throws IOException {
        try {
            List<com.azure.ai.vision.face.models.FaceDetectionResult> azureFaces = azureFaceService
                    .detectFaces(imageBytes);

            List<FaceDetectionResult> results = new ArrayList<>();

            if (azureFaces.isEmpty()) {
                if (!request.isLive()) {
                    logVerification(null, FaceProvider.AZURE, false, 0.0, "None", "N/A", false);
                }
                return FaceVerificationResponse.failure("No faces detected by Azure");
            }

            for (com.azure.ai.vision.face.models.FaceDetectionResult face : azureFaces) {
                com.azure.ai.vision.face.models.FaceRectangle rect = face.getFaceRectangle();
                if (rect == null) {
                    log.warn("Azure detected a face but returned no rectangle. Skipping.");
                    continue;
                }

                // Enhancement: Extract emotion using local OpenCV fallback since Azure retired
                // it
                String emotion = "Neutral";
                double livenessScore = 0.0;
                boolean isLive = true;

                try {
                    int x = rect.getLeft();
                    int y = rect.getTop();
                    int w = rect.getWidth();
                    int h = rect.getHeight();

                    // Clamp to image bounds
                    x = Math.max(0, x);
                    y = Math.max(0, y);
                    w = Math.min(w, fullImage.cols() - x);
                    h = Math.min(h, fullImage.rows() - y);

                    if (w > 0 && h > 0) {
                        Mat faceRoi = new Mat(fullImage, new Rect(x, y, w, h));
                        emotion = detectEmotion(faceRoi);
                        livenessScore = calculateLiveness(faceRoi);
                        isLive = livenessScore > LIVENESS_THRESHOLD;
                        faceRoi.release();
                    }
                } catch (Exception e) {
                    log.warn("Local analysis fallback failed for Azure face: {}", e.getMessage());
                }

                // Get age from Azure if available
                com.azure.ai.vision.face.models.FaceAttributes attributes = face.getFaceAttributes();
                String age = (attributes != null && attributes.getAge() != null)
                        ? String.valueOf(attributes.getAge().intValue())
                        : "N/A";

                FaceDetectionResult result = FaceDetectionResult.builder()
                        .x(rect.getLeft())
                        .y(rect.getTop())
                        .width(rect.getWidth())
                        .height(rect.getHeight())
                        .authorized(false) // Azure verification requires additional setup/PersonGroup
                        .confidence(0.0)
                        .isLive(isLive)
                        .livenessScore(livenessScore)
                        .emotion(emotion)
                        .age(age)
                        .build();

                results.add(result);

                // Log each detected face
                if (!request.isLive()) {
                    logVerification(null, FaceProvider.AZURE, false, 0.0, emotion, age, isLive);
                }
            }

            return FaceVerificationResponse.success(results);
        } catch (Exception e) {
            log.error("Azure Verification Failed: {}", e.getMessage());
            return FaceVerificationResponse.failure("Azure Error: " + e.getMessage());
        }
    }

    private void logVerification(String userId, FaceProvider provider, boolean authorized,
            double confidence, String emotion, String age, boolean isLive) {
        try {
            FaceVerificationLog logEntry = new FaceVerificationLog();
            logEntry.setUserId(userId);
            logEntry.setProvider(provider);
            logEntry.setAuthorized(authorized);
            logEntry.setConfidenceScore(confidence);
            logEntry.setDetectedEmotion(emotion);
            logEntry.setDetectedAge(age);
            logEntry.setLive(isLive);
            faceVerificationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save verification log: {}", e.getMessage());
        }
    }

    /**
     * Generate detailed analysis message for face detection result
     */
    private String getAnalysisMessage(AdvancedFaceDetail advancedFace, boolean authorized, boolean isLive) {
        StringBuilder message = new StringBuilder();

        if (advancedFace.isLikelySpoof()) {
            message.append("🚫 SPOOF RISK: ").append(String.format("%.1f%%", advancedFace.getSpoofProbability() * 100));
        } else if (authorized && isLive) {
            message.append("✅ AUTHORIZED - High confidence live detection");
        } else if (!isLive) {
            message.append("❌ NOT LIVE - Liveness check failed");
        } else if (!authorized) {
            message.append("⚠️ RECOGNIZED BUT NOT AUTHORIZED - Quality or confidence too low");
        }

        // Add quality information
        if (advancedFace.getQualityScore() < 0.6) {
            message.append(" | Low image quality detected");
        }

        // Add specific spoof indicators
        if (advancedFace.getOcclusionLevel() != null && advancedFace.getOcclusionLevel() > 0.5) {
            message.append(" | High occlusion detected");
        }

        if (advancedFace.getWearingSunglasses() != null && advancedFace.getWearingSunglasses()) {
            message.append(" | Sunglasses detected (spoof risk)");
        }

        return message.toString();
    }

    @Override
    public FaceVerificationResponse verifyFaceStream(FaceVerificationRequest request) throws IOException {
        // Single-flight lock
        if (activeRequests.incrementAndGet() > MAX_CONCURRENT_REQUESTS) {
            activeRequests.decrementAndGet();
            return FaceVerificationResponse.failure("Verification in progress");
        }

        // Optimized stream mode - skip logging for performance
        Mat image = null;
        File tempFile = null;

        try {
            // Read bytes once
            byte[] imageBytes = request.getImage().getBytes();

            // Create temp file
            String originalName = request.getImage().getOriginalFilename();
            String extension = ".jpg";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            tempFile = Files.createTempFile("stream_" + UUID.randomUUID() + "_", extension).toFile();

            try (var is = new java.io.ByteArrayInputStream(imageBytes)) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                return FaceVerificationResponse.failure("Could not load image for streaming verification");
            }

            int width = image.cols();
            int height = image.rows();

            if (request.getProvider() == FaceProvider.AWS && awsFaceService.isAvailable()) {
                return verifyFaceAws(request, imageBytes, image, width, height);
            } else if (request.getProvider() == FaceProvider.AZURE && azureFaceService.isAvailable()) {
                return verifyFaceAzure(request, imageBytes, image);
            }

            // Fast local processing for stream mode
            if (faceDetector == null && !initializationFailed) {
                initializeFaceDetector();
            }

            if (faceDetector == null) {
                return FaceVerificationResponse.failure("Face detection service is currently unavailable.");
            }

            // Use optimized detection
            List<Rect> faceRects = detectFacesOptimized(image);
            if (faceRects.isEmpty()) {
                return FaceVerificationResponse.failure("No faces detected");
            }

            // Limit to largest face for stream performance
            if (faceRects.size() > 1) {
                faceRects = faceRects.subList(0, 1);
            }

            List<FaceDetectionResult> detections = new ArrayList<>();
            List<FaceUser> allUsers = faceUserRepository.findAll();

            for (Rect rect : faceRects) {
                Mat faceRoi = null;
                Mat resizedFace = null;
                try {
                    faceRoi = new Mat(image, rect);
                    resizedFace = new Mat();
                    opencv_imgproc.resize(faceRoi, resizedFace, new Size(160, 160));

                    float[] features = extractFeatureVector(resizedFace);
                    String embedding = featuresToString(features);

                    // Fast liveness and emotion
                    double livenessScore = calculateLivenessFast(resizedFace);
                    boolean isLive = livenessScore > LIVENESS_THRESHOLD;
                    String emotion = detectEmotionFast(resizedFace);

                    // Quick identity match
                    double maxSimilarity = -1;
                    FaceUser matchedUser = null;

                    for (FaceUser user : allUsers) {
                        double similarity = compareFaceEmbeddings(embedding, user.getFaceEmbedding());
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                            matchedUser = user;
                        }
                    }

                    boolean authorized = maxSimilarity >= FACE_MATCH_THRESHOLD && isLive;

                    detections.add(FaceDetectionResult.builder()
                            .x(rect.x())
                            .y(rect.y())
                            .width(rect.width())
                            .height(rect.height())
                            .authorized(authorized)
                            .user(authorized ? matchedUser : null)
                            .confidence(maxSimilarity)
                            .isLive(isLive)
                            .livenessScore(livenessScore)
                            .emotion(emotion)
                            .age("N/A")
                            .moving(false)
                            .build());

                } finally {
                    if (faceRoi != null)
                        faceRoi.release();
                    if (resizedFace != null)
                        resizedFace.release();
                }
            }

            return FaceVerificationResponse.success(detections);

        } catch (Exception e) {
            log.error("Stream verification error: {}", e.getMessage());
            return FaceVerificationResponse.failure("Stream verification failed: " + e.getMessage());
        } finally {
            activeRequests.decrementAndGet();
            if (image != null)
                image.release();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Override
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("modelLoaded", modelLoaded);
        status.put("initializationFailed", initializationFailed);
        status.put("awsAvailable", awsFaceService.isAvailable());
        status.put("azureAvailable", azureFaceService.isAvailable());
        status.put("totalRegisteredUsers", faceUserRepository.count());
        status.put("faceMatchThreshold", FACE_MATCH_THRESHOLD);
        status.put("livenessThreshold", LIVENESS_THRESHOLD);
        status.put("detectionConfidence", FACE_DETECTION_CONFIDENCE);
        status.put("maxFacesToProcess", MAX_FACES_TO_PROCESS);
        status.put("faceSizeThreshold", FACE_SIZE_THRESHOLD);

        return status;
    }

    /**
     * Preprocess face image - detect face, validate brightness and liveness
     * This method performs NO database operations
     */
    private PreprocessedFaceData preprocess(FaceRegistrationRequest request) throws IOException {
        log.debug("Starting face preprocessing for email: {}", request.getEmail());

        Mat image = null;
        File tempFile = null;

        try {
            // Create temporary file for OpenCV processing
            String originalName = request.getImage().getOriginalFilename();
            String extension = ".jpg";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            tempFile = Files.createTempFile("preprocess_" + UUID.randomUUID() + "_", extension).toFile();

            try (var is = request.getImage().getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Invalid image file");
            }

            // Initialize face detector if needed
            if (faceDetector == null && !initializationFailed) {
                initializeFaceDetector();
            }
            if (initializationFailed) {
                throw new RuntimeException("Face detection service is not available");
            }

            // Detect faces
            List<Rect> faceRects = detectFaces(image);

            // Exactly ONE face validation
            if (faceRects.isEmpty()) {
                throw new IllegalArgumentException("No faces detected in the image");
            }
            if (faceRects.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple faces detected. Please provide an image with exactly one face.");
            }

            // Brightness validation
            Mat gray = new Mat();
            opencv_imgproc.cvtColor(image, gray, opencv_imgproc.COLOR_BGR2GRAY);
            org.bytedeco.opencv.opencv_core.Scalar meanScalar = org.bytedeco.opencv.global.opencv_core.mean(gray);
            double brightness = meanScalar.get(0);
            gray.release();

            if (brightness < 50) {
                throw new IllegalArgumentException("Image too dark. Please provide a brighter image.");
            }

            // Liveness check
            Rect faceRect = faceRects.get(0);
            Mat faceRoi = new Mat(image, faceRect);
            Mat resizedFace = new Mat();
            opencv_imgproc.resize(faceRoi, resizedFace, new Size(160, 160));
            double livenessScore = calculateLivenessFast(resizedFace);
            resizedFace.release();
            faceRoi.release();

            if (livenessScore < 15.0) {
                throw new IllegalArgumentException("Potential spoof detected. Please use a real face photo.");
            }

            // Extract face features (only ONCE)
            String faceEmbedding = extractFaceFeatures(request.getImage());

            // Convert image to Base64
            String imageData = null;
            try {
                byte[] imageBytes = request.getImage().getBytes();
                imageData = "data:" + request.getImage().getContentType() + ";base64,"
                        + java.util.Base64.getEncoder().encodeToString(imageBytes);
            } catch (IOException e) {
                log.warn("Failed to encode image as Base64: {}", e.getMessage());
            }

            return new PreprocessedFaceData(imageData, faceEmbedding);

        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("Failed to preprocess image: " + e.getMessage(), e);
        } finally {
            if (image != null) {
                image.release();
            }
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * Check for duplicate faces using efficient projection query
     * This method performs READ-ONLY database operations
     */
    private void checkDuplicate(String embedding) {
        log.debug("Starting duplicate check for face embedding");

        List<FaceEmbeddingView> existingEmbeddings = faceUserRepository.findAllEmbeddings();

        for (FaceEmbeddingView view : existingEmbeddings) {
            if (compareFaceEmbeddings(embedding, view.getFaceEmbedding()) >= 0.9) {
                log.warn("Duplicate face detected during registration");
                throw new IllegalArgumentException("User already registered (Face match)");
            }
        }

        log.debug("No duplicate faces found");
    }
}
