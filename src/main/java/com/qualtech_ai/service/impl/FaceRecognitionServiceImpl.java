package com.qualtech_ai.service.impl;

import com.qualtech_ai.dto.FaceRegistrationRequest;
import com.qualtech_ai.dto.FaceVerificationRequest;
import com.qualtech_ai.dto.FaceVerificationResponse;
import com.qualtech_ai.dto.FaceDetectionResult;
import com.qualtech_ai.service.S3Service;
import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.exception.ResourceNotFoundException;
import com.qualtech_ai.repository.FaceUserRepository;
import com.qualtech_ai.service.FaceRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import static org.bytedeco.opencv.global.opencv_core.CV_32F;
import static org.bytedeco.opencv.global.opencv_dnn.blobFromImage;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;

@Slf4j
@Service
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    private static final double FACE_MATCH_THRESHOLD = 0.6; // Cosine similarity threshold
    private static final double FACE_DETECTION_CONFIDENCE = 0.5; // Minimum confidence for face detection

    // DNN model paths - Using Caffe models
    private static final String FACE_DETECTOR_MODEL_RES = "classpath:face_models/deploy.prototxt";
    private static final String FACE_DETECTOR_WEIGHTS_RES = "classpath:face_models/res10_300x300_ssd_iter_140000.caffemodel";

    // Image preprocessing parameters
    private static final int INPUT_WIDTH = 300;
    private static final int INPUT_HEIGHT = 300;
    private static final int FEATURE_SIZE = 128; // Standard face embedding size

    private final FaceUserRepository faceUserRepository;
    private final S3Service s3Service;
    private final ResourceLoader resourceLoader;

    public FaceRecognitionServiceImpl(
            FaceUserRepository faceUserRepository,
            S3Service s3Service,
            ResourceLoader resourceLoader) {
        this.faceUserRepository = faceUserRepository;
        this.s3Service = s3Service;
        this.resourceLoader = resourceLoader;
    }

    // DNN-based face detector
    private Net faceDetector;

    @Override
    @Transactional
    public FaceUser registerFace(FaceRegistrationRequest request) throws IOException {
        // Check if user with this email already exists
        if (faceUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        // Process the image and extract face features
        String faceEmbedding = extractFaceFeatures(request.getImage());

        // Upload image to S3
        String s3Key = s3Service.generateS3Key("faces", request.getImage().getOriginalFilename());
        String imageUrl = s3Service.uploadFile(request.getImage(), s3Key);

        // Save user to database
        FaceUser user = new FaceUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setFaceEmbedding(faceEmbedding);
        user.setImageUrl(imageUrl);
        user.setS3Key(s3Key);
        user.setDepartment(request.getDepartment());
        user.setPosition(request.getPosition());

        return faceUserRepository.save(user);
    }

    @Override
    public FaceVerificationResponse verifyFace(FaceVerificationRequest request) throws IOException {
        Mat image = null;
        File tempFile = null;

        try {
            log.info("Processing multi-person verification request");

            // Prepare temporary file for OpenCV
            String safeFilename = Objects.toString(request.getImage().getOriginalFilename(), "unknown")
                    .replaceAll("[^a-zA-Z0-9.-]", "_");
            tempFile = new File(System.getProperty("java.io.tmpdir"),
                    "verify_" + System.currentTimeMillis() + "_" + safeFilename);
            try (var is = request.getImage().getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IOException("Could not load image for verification");
            }

            if (faceDetector == null) {
                initializeFaceDetector();
            }

            // Detect ALL faces in the frame
            List<Rect> faceRects = detectFaces(image);
            if (faceRects.isEmpty()) {
                return FaceVerificationResponse.failure("No faces detected in the image");
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

                    // Identify the person
                    double maxSimilarity = -1;
                    FaceUser matchedUser = null;

                    for (FaceUser user : allUsers) {
                        double similarity = compareFaceEmbeddings(embedding, user.getFaceEmbedding());
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                            matchedUser = user;
                        }
                    }

                    boolean isAuthorized = maxSimilarity >= FACE_MATCH_THRESHOLD;

                    // NEW: Calculate Liveness, Emotion, and Motion
                    double livenessScore = calculateLiveness(resizedFace);
                    boolean isLive = livenessScore > 10.0; // Threshold for Laplacian variance
                    String emotion = detectEmotion(resizedFace);
                    boolean isMoving = false; // Initial state, will be updated by client-side tracking or temporal
                                              // check if state persisted

                    detections.add(FaceDetectionResult.builder()
                            .x(rect.x())
                            .y(rect.y())
                            .width(rect.width())
                            .height(rect.height())
                            .authorized(isAuthorized)
                            .user(isAuthorized ? matchedUser : null)
                            .confidence(maxSimilarity)
                            .isLive(isLive)
                            .livenessScore(livenessScore)
                            .emotion(emotion)
                            .moving(isMoving)
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
            log.error("Error during multi-person verification: {}", e.getMessage(), e);
            return FaceVerificationResponse.failure("Face analysis failed: " + e.getMessage());
        } finally {
            if (image != null)
                image.release();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Override
    public List<FaceUser> getAllFaceUsers() {
        return faceUserRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteFaceUser(String id) {
        FaceUser user = faceUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Delete the image from S3
        if (user.getS3Key() != null) {
            s3Service.deleteFile(user.getS3Key());
        }

        // Delete the user from the database
        faceUserRepository.delete(user);
    }

    @Override
    public byte[] processImage(MultipartFile imageFile) throws IOException {
        // This is a placeholder for image processing logic
        // In a real implementation, you might want to detect faces, draw bounding
        // boxes, etc.
        return imageFile.getBytes();
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
        Mat resizedFace = null;
        File tempFile = null;

        try {
            // Create a temporary file with null-safe filename
            String safeFilename = Objects.toString(imageFile.getOriginalFilename(), "unknown")
                    .replaceAll("[^a-zA-Z0-9.-]", "_"); // Sanitize filename
            tempFile = new File(System.getProperty("java.io.tmpdir"),
                    "face_" + System.currentTimeMillis() + "_" + safeFilename);
            // Ensure parent directory exists
            File parent = tempFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (var is = imageFile.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Read the image using OpenCV
            image = opencv_imgcodecs.imread(tempFile.getAbsolutePath(), IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IOException("Could not load image or image is empty");
            }

            // Initialize face detector if needed
            if (faceDetector == null) {
                initializeFaceDetector();
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

            // Resize face to standard size for feature extraction
            resizedFace = new Mat();
            opencv_imgproc.resize(faceRoi, resizedFace, new Size(160, 160));

            // Extract features from the face
            float[] features = extractFeatureVector(resizedFace);

            // Convert features to Base64 string for storage
            return featuresToString(features);

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
            if (resizedFace != null) {
                resizedFace.release();
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
        if (faceDetector != null)
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
            System.err.println("--- CRITICAL NATIVE ERROR ---");
            t.printStackTrace(System.err);
            log.error("CRITICAL: Failed to initialize face detector: {}", t.getMessage(), t);
            // Don't throw - allow app to run, just won't detect faces
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

            faceDetector.setInput(blob);
            detections = faceDetector.forward();

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
        // In a real implementation, you would use a pre-trained deep learning model
        // like FaceNet, ArcFace, or OpenFace to extract face embeddings.
        // This is a simplified placeholder implementation.

        // Convert to grayscale
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // Resize to a fixed size (e.g., 160x160 which is common for face recognition
        // models)
        Mat resized = new Mat();
        opencv_imgproc.resize(gray, resized, new Size(160, 160));

        // Normalize pixel values to [0,1]
        // Normalize
        Mat normalized = new Mat();
        opencv_core.normalize(gray, normalized, 0, 255, opencv_core.NORM_MINMAX, CV_32F, null);

        // Simple feature extraction: divide image into grid and compute statistics
        float[] features = new float[FEATURE_SIZE];
        int cellSize = 20;
        int idx = 0;

        for (int i = 0; i < normalized.rows() && idx < FEATURE_SIZE; i += cellSize) {
            for (int j = 0; j < normalized.cols() && idx < FEATURE_SIZE; j += cellSize) {
                int endRow = Math.min(i + cellSize, normalized.rows());
                int endCol = Math.min(j + cellSize, normalized.cols());

                Mat cell = new Mat(normalized, new Rect(j, i, endCol - j, endRow - i));
                Scalar mean = opencv_core.mean(cell);

                if (idx < FEATURE_SIZE) {
                    features[idx++] = (float) mean.get(0);
                }
                cell.release();
            }
        }

        gray.release();
        resized.release();
        normalized.release();

        return features;
    }

    /**
     * Compare two face embeddings using cosine similarity
     * Returns a similarity score between 0 and 1 (higher is more similar)
     */
    private double compareFaceEmbeddings(String embedding1, String embedding2) {
        try {
            float[] features1 = stringToFeatures(embedding1);
            float[] features2 = stringToFeatures(embedding2);

            // Calculate cosine similarity
            return cosineSimilarity(features1, features2);
        } catch (Exception e) {
            log.error("Error comparing face embeddings: {}", e.getMessage());
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
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        try {
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);

            Mat mean = new Mat();
            Mat stddev = new Mat();
            opencv_core.meanStdDev(laplacian, mean, stddev);

            double variance = Math.pow(stddev.createIndexer().getDouble(0), 2);
            return variance;
        } catch (Exception e) {
            log.error("Error calculating liveness: {}", e.getMessage());
            return 0.0;
        } finally {
            gray.release();
            laplacian.release();
        }
    }

    /**
     * Detect emotion based on basic facial features (pixel intensity distribution)
     */
    private String detectEmotion(Mat face) {
        // Simplified emotion detection: Analyze the distribution of intensities
        // In a real app, this would use a CNN emotion classifier
        Mat gray = new Mat();
        try {
            opencv_imgproc.cvtColor(face, gray, opencv_imgproc.COLOR_BGR2GRAY);
            Scalar mean = opencv_core.mean(gray);
            double avgIntensity = mean.get(0);

            // Very raw heuristic: Higher intensity variance often correlates with more
            // expressive faces
            if (avgIntensity > 180)
                return "Surprised";
            if (avgIntensity < 80)
                return "Serious";

            return "Neutral";
        } catch (Exception e) {
            return "Unknown";
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
}
