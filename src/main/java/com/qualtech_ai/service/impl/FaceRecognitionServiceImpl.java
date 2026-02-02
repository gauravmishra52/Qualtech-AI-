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
import com.qualtech_ai.exception.CustomException;
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
import com.qualtech_ai.service.MultiFrameVerificationService;
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
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.opencv.global.opencv_core.CV_32F;
import static org.bytedeco.opencv.global.opencv_dnn.blobFromImage;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import org.bytedeco.opencv.opencv_core.MatVector;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    private static final double FACE_MATCH_THRESHOLD = 0.70; // Relaxed for better usability in common lighting

    private static final double FACE_DETECTION_CONFIDENCE = 0.45; // Refined for faster detection
    private static final int MAX_FACES_TO_PROCESS = 3; // Limit for real-time performance

    // DNN model paths - Using Caffe models
    private static final String FACE_DETECTOR_MODEL_RES = "classpath:face_models/deploy.prototxt";
    private static final String FACE_DETECTOR_WEIGHTS_RES = "classpath:face_models/res10_300x300_ssd_iter_140000.caffemodel";

    // Image preprocessing parameters - Optimized for speed
    private static final int INPUT_WIDTH = 300;
    private static final int INPUT_HEIGHT = 300;
    private static final int FEATURE_SIZE = 128; // Standard face embedding size

    // Performance optimization constants
    private static final int FACE_SIZE_THRESHOLD = 40; // Minimum face size for detection
    private static final double LIVENESS_THRESHOLD = 45.0; // Balanced for security and usability (reduced to handle
                                                           // screen glow)

    private final FaceUserRepository faceUserRepository;
    private final S3Service s3Service;
    private final ResourceLoader resourceLoader;
    private final AwsFaceService awsFaceService;
    private final AzureFaceService azureFaceService;
    private final FaceVerificationLogRepository faceVerificationLogRepository;
    private final FaceImagePreprocessor faceImagePreprocessor;
    private final FaceUserTxService faceUserTxService;
    private final MultiFrameVerificationService multiFrameVerificationService;
    // AdaptiveThresholdService disabled for stabilization - using fixed threshold
    // private final AdaptiveThresholdService adaptiveThresholdService;
    @Value("${face.recognition.threshold:0.85}")
    private double fixedThreshold;

    // SilentSelfImprovementService disabled for stabilization
    // private final SilentSelfImprovementService silentSelfImprovementService;
    private ObjectMapper objectMapper = new ObjectMapper(); // Non-final since it's directly initialized

    private AtomicInteger activeRequests = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_REQUESTS = 4;

    // Frame buffer for motion detection in stream mode
    private ConcurrentHashMap<String, List<byte[]>> streamFrameBuffers = new ConcurrentHashMap<String, List<byte[]>>();
    private static final int STREAM_BUFFER_SIZE = 3; // Number of frames to collect before multi-frame check

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

        // 3.5 Check for duplicates in AWS (Deep Fix)
        checkAwsDuplicate(request.getImage());

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
    @Transactional(readOnly = true)
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
                    localLivenessScore = calculateLiveness(resizedLiveness);
                    localLivenessPassed = localLivenessScore > LIVENESS_THRESHOLD;
                    resizedLiveness.release();
                    faceRoi.release();
                }
            } catch (Exception e) {
                log.error("Local preprocessing error: {}", e.getMessage());
            }

            // 2. Delegate to the strongest available provider (GOLDEN RULE: No weak paths)
            int width = image.cols();
            int height = image.rows();

            if (awsFaceService.isAvailable()) {
                log.info("Routing verification through Advanced AWS Security Path");
                return verifyFaceAws(request, imageBytes, image, width, height);
            } else if (azureFaceService.isAvailable()) {
                log.info("Routing verification through Azure Path (Fallback)");
                return verifyFaceAzure(request, imageBytes, image);
            } else {
                return FaceVerificationResponse.failure("No cloud provider available for secure verification");
            }

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

                mean.release();
                stddev.release();
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
    /**
     * Calculate liveness score (0-100) with strict anti-spoofing for screens and
     * frames
     */
    private double calculateLiveness(Mat face) {
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        Mat hsv = new Mat();
        Mat reflectionMask = new Mat();
        MatVector channels = new MatVector();

        try {
            // 1. Texture/Sharpness Analysis (Laplacian Variance)
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);

            double variance = 0;
            Mat mean = new Mat();
            Mat stddev = new Mat();
            try {
                opencv_core.meanStdDev(laplacian, mean, stddev);
                variance = Math.pow(stddev.createIndexer().getDouble(0), 2);
            } finally {
                mean.release();
                stddev.release();
            }

            // Adjusted for 160x160 face crops: variance of 100-200 is common for sharp
            // faces
            // Previous formula (variance - 50) / 7.5 was too strict for real-time
            // mobile/web streams
            double textureScore = Math.min(100, Math.max(0, (variance - 25) * 1.0));

            // 2. Reflection Detection (Glare from screens or glass frames)
            opencv_imgproc.cvtColor(face, hsv, opencv_imgproc.COLOR_BGR2HSV);

            opencv_core.inRange(hsv,
                    new Mat(new Scalar(0, 0, 220, 0)), // High Value (Brightness)
                    new Mat(new Scalar(180, 50, 255, 0)), // Low Saturation (White/Glare)
                    reflectionMask);
            double reflectionRatio = (double) opencv_core.countNonZero(reflectionMask) / (face.rows() * face.cols());

            // 3. Color Balance Analysis (Screens often emit excessive Blue light)
            opencv_core.split(face, channels);

            double rToB = 1.0;
            Mat bMean = new Mat(), rMean = new Mat();
            Mat bStd = new Mat(), rStd = new Mat();
            try {
                opencv_core.meanStdDev(channels.get(0), bMean, bStd); // Blue channel
                opencv_core.meanStdDev(channels.get(2), rMean, rStd); // Red channel

                double blueAvg = bMean.createIndexer().getDouble(0);
                double redAvg = rMean.createIndexer().getDouble(0);

                rToB = (blueAvg > 0) ? redAvg / blueAvg : 1.0;
            } finally {
                bMean.release();
                rMean.release();
                bStd.release();
                rStd.release();
            }

            // --- SCORING LOGIC ---
            double finalScore = 50.0; // Start with a stable base score for a detected face

            // Texture Bonus: Real skin has natural texture/sharpness
            if (variance > 60 && variance < 1000) {
                finalScore += 20.0;
            }

            // --- PENALTIES (Strict Gates) ---

            // Penalty: Blue Light (Screen indication)
            if (rToB <= 1.0) { // Relaxed from 1.2
                finalScore -= 30.0; // Reduced penalty
                log.debug("Liveness Penalty: Blue light detected (R/B ratio: {})", rToB);
            } else if (rToB < 1.1) { // Mild penalty for cool lighting that mimics screens
                finalScore -= 15.0;
            }

            // Penalty: Reflections (Screen/Glass)
            if (reflectionRatio > 0.10) { // Relaxed from 0.04
                finalScore -= 100.0; // Immediate fail
                log.debug("Liveness Penalty: High reflection detected ({}%)", reflectionRatio * 100);
            } else if (reflectionRatio > 0.03) { // Relaxed from 0.005
                finalScore -= 20.0;
            }

            // Penalty: Too Smooth (Blurry Photo) or Too Grainy (Moiré pattern/Screen noise)
            if (variance < 40) { // Stricter blur threshold
                finalScore -= 70.0;
                log.debug("Liveness Penalty: Image too smooth/blurry (Var: {})", variance);
            } else if (variance > 1400) {
                finalScore -= 80.0;
                log.debug("Liveness Penalty: High frequency noise (Moiré pattern likely) (Var: {})", variance);
            }

            // Clamp 0-100
            finalScore = Math.max(0, Math.min(100, finalScore));

            log.info("Liveness Check: Score={} (Var={}, Reflect={}%, R/B={})",
                    finalScore, variance, reflectionRatio * 100, rToB);

            return finalScore;

        } catch (Exception e) {
            log.error("Error calculating liveness: {}", e.getMessage());
            return 0.0; // Fail safe
        } finally {
            gray.release();
            laplacian.release();
            hsv.release();
            reflectionMask.release();
            channels.close(); // Important: release split channels
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
            double avgIntensity = 0;
            double variance = 0;
            Mat mean = new Mat();
            Mat stddev = new Mat();
            try {
                opencv_core.meanStdDev(gray, mean, stddev);
                avgIntensity = mean.createIndexer().getDouble(0);
                variance = Math.pow(stddev.createIndexer().getDouble(0), 2);
            } finally {
                mean.release();
                stddev.release();
            }

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

                // C. Authorization Decision (Industry Secure Flow)
                if (matchedUser != null) {
                    // STEP 1: Liveness Gate (Hard Gate)
                    if (isSpoofed) {
                        authorized = false;
                        log.warn("SECURITY ALERT: AWS Spoof detection triggered for user {}. Access Denied.", userId);
                    } else if (!isLive) {
                        authorized = false;
                        log.warn("Liveness FAILED (Score: {}) - blocking authentication for user {}", livenessScore,
                                userId);
                    } else {
                        // STEP 2: Similarity Check
                        // Similarity is 0.0 to 1.0. We want a base of 0.7 for strong match.
                        double similarityScore = confidence;

                        // STEP 3: Quality Bonus (Add up to 0.1 bonus for high quality)
                        double qualityBonus = (qualityScore / 100.0) * 0.1;

                        double finalAuthScore = similarityScore + qualityBonus;

                        // Use the configured FACE_MATCH_THRESHOLD (0.55) as the gate for finalAuthScore
                        authorized = finalAuthScore >= FACE_MATCH_THRESHOLD;

                        log.info(
                                "Auth Decision - User: {}, FinalScore: {} (Threshold: {}), [Similarity: {}, QualBonus: {}]",
                                userId, finalAuthScore, FACE_MATCH_THRESHOLD, similarityScore, qualityBonus);
                    }
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
                        .eyesOpen(advancedFace.getEyesOpen())
                        .pitch(advancedFace.getPitch())
                        .roll(advancedFace.getRoll())
                        .yaw(advancedFace.getYaw())
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

            // 5. PER-FACE SECURITY AUDIT (Identity Trust Model)
            // Authorize real person, flag spoofs, and label other faces
            for (FaceDetectionResult res : results) {
                // Local Liveness Fallback (Hard Fail if extremely low score)
                if (res.getLivenessScore() < 20.0) {
                    res.setAuthorized(false);
                    res.setLive(false);
                    res.setIsSpoofed(true);
                    res.setAnalysisMessage("🚨 SPOOF DETECTED: Static/Phone screen artifact.");
                }

                // Consistency check with AWS Spoof Flags
                if (Boolean.TRUE.equals(res.getIsSpoofed())
                        && (res.getAnalysisMessage() == null || !res.getAnalysisMessage().contains("SPOOF"))) {
                    res.setAnalysisMessage("🚨 SECURITY RISK: AWS detected spoofing attempt.");
                }

                if (!res.isAuthorized() && !Boolean.TRUE.equals(res.getIsSpoofed())) {
                    if (res.getUser() == null) {
                        res.setAnalysisMessage("👤 UNKNOWN: Unregistered face.");
                    } else if (res.getConfidence() < FACE_MATCH_THRESHOLD) {
                        res.setAnalysisMessage("⚠️ LOW CONFIDENCE: Similarity below threshold.");
                    }
                }
            }

            if (results.isEmpty()) {
                return FaceVerificationResponse.failure("No faces detected by AWS");
            }

            // Provide detailed feedback about spoof attempts from the group
            if (advancedAnalysis.hasSpoofAttempts()) {
                log.warn("Potential spoofing detected in frame: {}", advancedAnalysis.getAnalysisSummary());
            }

            // Sort results: Authorized first, then Spoofed, then others
            results.sort((a, b) -> {
                if (a.isAuthorized() != b.isAuthorized())
                    return a.isAuthorized() ? -1 : 1;
                if (Boolean.TRUE.equals(a.getIsSpoofed()) != Boolean.TRUE.equals(b.getIsSpoofed()))
                    return Boolean.TRUE.equals(a.getIsSpoofed()) ? -1 : 1;
                return Double.compare(b.getConfidence(), a.getConfidence());
            });

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

            // Post-processing: Singularity check for Azure
            // Post-processing: Mark spoofs for Azure but don't reject the whole frame
            for (FaceDetectionResult res : results) {
                if (res.getLivenessScore() < 20.0) {
                    res.setAuthorized(false);
                    res.setIsSpoofed(true);
                    res.setAnalysisMessage("🚨 SECURITY RISK: Spoof detected.");
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
            message.append("👤 RECOGNIZED: Also present in frame");
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
    @Transactional(readOnly = true)
    public FaceVerificationResponse verifyFaceStream(FaceVerificationRequest request) throws IOException {
        // Single-flight lock
        if (activeRequests.incrementAndGet() > MAX_CONCURRENT_REQUESTS) {
            activeRequests.decrementAndGet();
            return FaceVerificationResponse.failure("Verification in progress");
        }

        Mat image = null;
        File tempFile = null;

        try {
            // Multi-frame buffering logic to enable motion detection
            String correlationId = request.getCorrelationId();
            if (correlationId == null) {
                correlationId = "anonymous-stream-" + Thread.currentThread().getName();
            }

            byte[] imageBytes = request.getImage().getBytes();

            // Add to thread-safe buffer
            List<byte[]> buffer = streamFrameBuffers.computeIfAbsent(correlationId,
                    k -> new java.util.concurrent.CopyOnWriteArrayList<>());
            buffer.add(imageBytes);

            // If we have enough frames, trigger multi-frame analysis (motion detection)
            if (buffer.size() >= STREAM_BUFFER_SIZE) {
                log.info("Buffer full for stream {}. Triggering multi-frame analysis...", correlationId);
                List<MultipartFile> frames = convertToMultipartFiles(buffer, request.getImage().getOriginalFilename(),
                        request.getImage().getContentType());

                // Critical: Remove buffer before processing to avoid re-entry issues
                streamFrameBuffers.remove(correlationId);

                return multiFrameVerificationService.verifyWithMultipleFrames(frames, request, this);
            }

            // Optimized stream mode - skip logging for performance

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

            // Identity Trust Model: Evaluate all faces in the frame individually
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

                    // 1. Local Liveness & Spoof Check
                    double livenessScore = calculateLiveness(resizedFace);
                    boolean isLive = livenessScore > LIVENESS_THRESHOLD;
                    boolean isSpoofed = livenessScore < 20.0;
                    String emotion = detectEmotion(resizedFace);

                    // 2. Identity Search
                    double maxSimilarity = -1;
                    FaceUser matchedUser = null;

                    for (FaceUser user : allUsers) {
                        double similarity = compareFaceEmbeddings(embedding, user.getFaceEmbedding());
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                            matchedUser = user;
                        }
                    }

                    // 3. Per-Face Authorization
                    // authorized only if live, matching, and not spoofed
                    boolean authorized = isLive && !isSpoofed && maxSimilarity >= FACE_MATCH_THRESHOLD;

                    String analysisMsg = null;
                    if (isSpoofed)
                        analysisMsg = "🚨 SPOOF DETECTED";
                    else if (!isLive)
                        analysisMsg = "❌ NOT LIVE";
                    else if (!authorized && matchedUser == null)
                        analysisMsg = "👤 UNKNOWN";

                    detections.add(FaceDetectionResult.builder()
                            .x(rect.x())
                            .y(rect.y())
                            .width(rect.width())
                            .height(rect.height())
                            .authorized(authorized)
                            .user(matchedUser)
                            .confidence(maxSimilarity)
                            .isLive(isLive && !isSpoofed)
                            .livenessScore(livenessScore)
                            .isSpoofed(isSpoofed)
                            .emotion(emotion)
                            .age("N/A")
                            .analysisMessage(analysisMsg)
                            .build());

                } finally {
                    if (faceRoi != null)
                        faceRoi.release();
                    if (resizedFace != null)
                        resizedFace.release();
                }
            }

            // Sort results: Authorized first, then Spoofed, then others
            detections.sort((a, b) -> {
                if (a.isAuthorized() != b.isAuthorized())
                    return a.isAuthorized() ? -1 : 1;
                if (Boolean.TRUE.equals(a.getIsSpoofed()) != Boolean.TRUE.equals(b.getIsSpoofed()))
                    return Boolean.TRUE.equals(a.getIsSpoofed()) ? -1 : 1;
                return Double.compare(b.getConfidence(), a.getConfidence());
            });

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
        Mat faceRoi = null;
        Mat resizedFace = null;
        File tempFile = null;

        try {
            byte[] imageBytes = request.getImage().getBytes();
            tempFile = Files.createTempFile("preprocess_" + UUID.randomUUID() + "_", ".jpg").toFile();
            Files.write(tempFile.toPath(), imageBytes);

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Invalid image file");
            }

            if (faceDetector == null && !initializationFailed) {
                initializeFaceDetector();
            }
            if (initializationFailed) {
                throw new RuntimeException("Face detection service is not available");
            }

            List<Rect> faceRects = detectFaces(image);
            if (faceRects.isEmpty()) {
                throw new IllegalArgumentException("No faces detected in the image");
            }
            if (faceRects.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple faces detected. Please provide an image with exactly one face.");
            }

            Rect faceRect = faceRects.get(0);
            faceRoi = new Mat(image, faceRect);

            // Validation: Brightness
            Mat gray = new Mat();
            opencv_imgproc.cvtColor(faceRoi, gray, opencv_imgproc.COLOR_BGR2GRAY);
            double brightness = opencv_core.mean(gray).get(0);
            gray.release();

            if (brightness < 40) {
                throw new IllegalArgumentException(
                        "Face is too dark. Adjust lighting (Brightness: " + (int) brightness + ")");
            }

            // Validation: Liveness (Hard Gate at Registration)
            resizedFace = new Mat();
            opencv_imgproc.resize(faceRoi, resizedFace, new Size(160, 160));
            double livenessScore = calculateLiveness(resizedFace);
            if (livenessScore < 40.0) { // Registration requires decent liveness
                throw new IllegalArgumentException(
                        "Registration denied: Quality/Liveness too low. Avoid using screens.");
            }

            // Extraction: Features
            float[] features = extractFeatureVector(resizedFace);
            String faceEmbedding = featuresToString(features);

            // Response: Encode original image
            String imageData = "data:" + request.getImage().getContentType() + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(imageBytes);

            return new PreprocessedFaceData(imageData, faceEmbedding);

        } finally {
            if (image != null)
                image.release();
            if (faceRoi != null)
                faceRoi.release();
            if (resizedFace != null)
                resizedFace.release();
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
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
            if (compareFaceEmbeddings(embedding, view.getFaceEmbedding()) >= FACE_MATCH_THRESHOLD) {
                log.warn("Duplicate face detected during registration (Local Check)");
                throw new CustomException("This face is already registered in the system (Local Match).");
            }
        }

        log.debug("No duplicate faces found locally");
    }

    /**
     * Check for duplicate faces using AWS Rekognition (Deep Fix)
     */
    private void checkAwsDuplicate(MultipartFile image) {
        if (awsFaceService.isAvailable()) {
            try {
                software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse response = awsFaceService
                        .searchFace(image.getBytes());

                if (response != null && !response.faceMatches().isEmpty()) {
                    float similarity = response.faceMatches().get(0).similarity();
                    log.warn("Duplicate face detected in AWS with similarity: {}", similarity);
                    throw new CustomException("This face is already authorized in the system (Cloud Match).");
                }
            } catch (IOException e) {
                log.error("Error reading image for AWS check", e);
            } catch (Exception e) {
                if (e instanceof CustomException)
                    throw e;
                log.warn("AWS duplicate check skipped due to error: {}", e.getMessage());
            }
        }
    }

    private List<MultipartFile> convertToMultipartFiles(List<byte[]> buffers, String originalName, String contentType) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < buffers.size(); i++) {
            final byte[] content = buffers.get(i);
            final String name = "frame_" + i;
            files.add(new MultipartFile() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getOriginalFilename() {
                    return originalName;
                }

                @Override
                public String getContentType() {
                    return contentType;
                }

                @Override
                public boolean isEmpty() {
                    return content.length == 0;
                }

                @Override
                public long getSize() {
                    return content.length;
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return content;
                }

                @Override
                public java.io.InputStream getInputStream() throws IOException {
                    return new java.io.ByteArrayInputStream(content);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    Files.write(dest.toPath(), content);
                }
            });
        }
        return files;
    }
}
