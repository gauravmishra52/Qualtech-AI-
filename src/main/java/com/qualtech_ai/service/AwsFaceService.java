package com.qualtech_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import com.qualtech_ai.dto.AdvancedFaceDetail;
import com.qualtech_ai.dto.AdvancedFaceAnalysisResult;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class AwsFaceService {

    private final RekognitionClient rekognitionClient;

    @Value("${aws.rekognition.collection-id:qualtech-faces}")
    private String collectionId;

    public AwsFaceService(Optional<RekognitionClient> rekognitionClient) {
        this.rekognitionClient = rekognitionClient.orElse(null);
        if (this.rekognitionClient == null) {
            log.warn("AWS Rekognition Client is not configured. AWS features will be disabled.");
        }
    }

    @PostConstruct
    public void validateConfiguration() {
        if (rekognitionClient == null)
            return;

        try {
            log.info("Validating AWS Rekognition Configuration...");
            ListCollectionsResponse response = rekognitionClient
                    .listCollections(ListCollectionsRequest.builder().maxResults(1).build());
            log.info("AWS Rekognition Connection Successful. Found collections: {}", response.collectionIds());
            ensureCollectionExists();
        } catch (Exception e) {
            log.error("AWS Rekognition Validation Failed! Check Region/Credentials. Error: {}", e.getMessage());
            // We don't throw here to allow app to start, but AWS features will likely fail
        }
    }

    public SearchFacesByImageResponse searchFace(byte[] imageBytes) {
        if (rekognitionClient == null) {
            log.warn("❌ AWS Rekognition is NOT CONFIGURED. Skipping search.");
            return null;
        }

        Image image = Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();

        try {
            log.debug("🔍 AWS: Searching collection '{}' (threshold: 80%)", collectionId);

            SearchFacesByImageRequest request = SearchFacesByImageRequest.builder()
                    .collectionId(collectionId)
                    .image(image)
                    .maxFaces(1)
                    .faceMatchThreshold(80F) // Increased to 80.0 as per stability requirements
                    .build();

            SearchFacesByImageResponse response = rekognitionClient.searchFacesByImage(request);

            // Log success with details
            if (response != null && !response.faceMatches().isEmpty()) {
                var match = response.faceMatches().get(0);
                log.info("✅ AWS MATCH! ExternalId: {}, FaceId: {}, Similarity: {:.2f}%",
                        match.face().externalImageId(), match.face().faceId(), match.similarity());
            } else if (response != null) {
                log.warn("⚠️  AWS: No matches found in collection '{}'", collectionId);
            }

            return response;
        } catch (ResourceNotFoundException e) {
            log.error("❌ AWS: Collection '{}' NOT FOUND!", collectionId);
            return null;
        } catch (InvalidParameterException e) {
            log.error("❌ AWS: Invalid parameters: {}", e.getMessage());
            return null;
        } catch (AccessDeniedException e) {
            log.error("❌ AWS: ACCESS DENIED! Check IAM permissions.");
            return null;
        } catch (RekognitionException e) {
            log.error("❌ AWS Error [{}]: {}", e.awsErrorDetails().errorCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("❌ AWS Unexpected error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    public DetectFacesResponse detectFaces(byte[] imageBytes) {
        if (rekognitionClient == null) {
            log.warn("Attempted to detect faces but AWS Rekognition is not configured.");
            return null;
        }

        Image image = Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();

        try {
            DetectFacesRequest request = DetectFacesRequest.builder()
                    .image(image)
                    .attributes(Attribute.ALL) // Include all attributes for advanced analysis
                    .build();

            return rekognitionClient.detectFaces(request);
        } catch (RekognitionException e) {
            log.error("AWS Rekognition Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Advanced face detection with quality analysis and spoof detection
     */
    public AdvancedFaceAnalysisResult analyzeFaceAdvanced(byte[] imageBytes) {
        if (rekognitionClient == null) {
            log.warn("Attempted advanced face analysis but AWS Rekognition is not configured.");
            return null;
        }

        Image image = Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();

        try {
            DetectFacesRequest request = DetectFacesRequest.builder()
                    .image(image)
                    .attributes(Attribute.ALL)
                    .build();

            DetectFacesResponse response = rekognitionClient.detectFaces(request);

            List<AdvancedFaceDetail> advancedFaces = new ArrayList<>();
            for (FaceDetail face : response.faceDetails()) {
                AdvancedFaceDetail advancedDetail = analyzeFaceQuality(face);
                advancedFaces.add(advancedDetail);
            }

            return AdvancedFaceAnalysisResult.builder()
                    .faceDetails(advancedFaces)
                    .totalFaces(response.faceDetails().size())
                    .build();

        } catch (RekognitionException e) {
            log.error("AWS Advanced Face Analysis Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Analyze individual face quality and detect potential spoofing
     */
    private AdvancedFaceDetail analyzeFaceQuality(FaceDetail face) {
        AdvancedFaceDetail.AdvancedFaceDetailBuilder builder = AdvancedFaceDetail.builder();

        // Basic face info
        BoundingBox box = face.boundingBox();
        builder.boundingBox(box);

        // Quality analysis
        if (face.quality() != null) {
            ImageQuality quality = face.quality();
            builder.sharpness(quality.sharpness() != null ? quality.sharpness().doubleValue() : null)
                    .brightness(quality.brightness() != null ? quality.brightness().doubleValue() : null);
        }

        // Pose analysis for anti-spoofing
        if (face.pose() != null) {
            Pose pose = face.pose();
            builder.pitch(pose.pitch() != null ? pose.pitch().doubleValue() : null)
                    .roll(pose.roll() != null ? pose.roll().doubleValue() : null)
                    .yaw(pose.yaw() != null ? pose.yaw().doubleValue() : null);
        }

        // Occlusion detection (faces with high occlusion might be photos)
        // Note: AWS Rekognition doesn't provide direct occlusion data in v2.x
        // We'll estimate occlusion based on other available features
        double estimatedOcclusion = 0.0;
        int occlusionFactors = 0;

        // High occlusion indicators
        if (face.sunglasses() != null && face.sunglasses().value()) {
            estimatedOcclusion += 0.3; // Sunglasses indicate eye occlusion
            occlusionFactors++;
        }

        if (face.eyeglasses() != null && face.eyeglasses().value()) {
            estimatedOcclusion += 0.1; // Eyeglasses indicate partial eye occlusion
            occlusionFactors++;
        }

        if (face.beard() != null && face.beard().value() && face.beard().confidence() > 0.8) {
            estimatedOcclusion += 0.15; // Large beard indicates lower face occlusion
            occlusionFactors++;
        }

        if (face.mustache() != null && face.mustache().value() && face.mustache().confidence() > 0.8) {
            estimatedOcclusion += 0.1; // Mustache indicates mouth area occlusion
            occlusionFactors++;
        }

        double totalOcclusion = occlusionFactors > 0 ? estimatedOcclusion / occlusionFactors : 0.0;
        builder.occlusionLevel(totalOcclusion);

        // Emotion analysis
        if (face.emotions() != null && !face.emotions().isEmpty()) {
            Emotion topEmotion = face.emotions().stream()
                    .max(java.util.Comparator.comparing(Emotion::confidence))
                    .orElse(null);
            if (topEmotion != null) {
                builder.topEmotion(topEmotion.type().toString())
                        .emotionConfidence(topEmotion.confidence());
            }
        }

        // Age range
        if (face.ageRange() != null) {
            AgeRange ageRange = face.ageRange();
            builder.ageLow(ageRange.low())
                    .ageHigh(ageRange.high());
        }

        // Gender
        if (face.gender() != null) {
            builder.gender(face.gender().value() != null ? face.gender().value().toString() : null)
                    .genderConfidence(face.gender().confidence());
        }

        // Facial features (beard, mustache, etc.)
        if (face.beard() != null) {
            builder.hasBeard(face.beard().value())
                    .beardConfidence(face.beard().confidence());
        }

        if (face.mustache() != null) {
            builder.hasMustache(face.mustache().value())
                    .mustacheConfidence(face.mustache().confidence());
        }

        if (face.eyeglasses() != null) {
            builder.wearingEyeglasses(face.eyeglasses().value())
                    .eyeglassesConfidence(face.eyeglasses().confidence());
        }

        if (face.sunglasses() != null) {
            builder.wearingSunglasses(face.sunglasses().value())
                    .sunglassesConfidence(face.sunglasses().confidence());
        }

        // Smile detection
        if (face.smile() != null) {
            builder.isSmiling(face.smile().value())
                    .smileConfidence(face.smile().confidence());
        }

        // Eyes open detection
        if (face.eyesOpen() != null) {
            builder.eyesOpen(face.eyesOpen().value())
                    .eyesOpenConfidence(face.eyesOpen().confidence());
        }

        // Mouth open detection
        if (face.mouthOpen() != null) {
            builder.mouthOpen(face.mouthOpen().value())
                    .mouthOpenConfidence(face.mouthOpen().confidence());
        }

        // Calculate spoof probability based on multiple factors
        double spoofProbability = calculateSpoofProbability(builder.build());
        builder.spoofProbability(spoofProbability);

        return builder.build();
    }

    /**
     * Calculate probability of spoofing based on various face attributes
     * Relaxed constraints to prevent false positives on webcam feeds
     */
    private double calculateSpoofProbability(AdvancedFaceDetail face) {
        double spoofScore = 0.0;

        // Factor 1: Very Low quality (only penalize extreme blur)
        // AWS Sharpness is 0-100. Threshold 15.0 covers very blurry faces.
        if (face.getSharpness() != null && face.getSharpness() < 15.0) {
            spoofScore += 0.15;
        }

        // Factor 2: Extreme pose angles
        boolean extremePose = false;
        if ((face.getPitch() != null && Math.abs(face.getPitch()) > 45) ||
                (face.getYaw() != null && Math.abs(face.getYaw()) > 45)) {
            extremePose = true;
        }
        if (extremePose) {
            spoofScore += 0.15;
        }

        // Factor 3: High occlusion
        if (face.getOcclusionLevel() != null && face.getOcclusionLevel() > 0.6) {
            spoofScore += 0.15;
        }

        // Factor 4: Sunglasses
        if (face.getWearingSunglasses() != null && face.getWearingSunglasses()) {
            spoofScore += 0.2;
        }

        // Factor 5: Eyes closed (suspicious but can happen naturally)
        if (face.getEyesOpen() != null && !face.getEyesOpen() &&
                face.getEyesOpenConfidence() != null && face.getEyesOpenConfidence() > 0.95) {
            spoofScore += 0.05; // Lowered penalty as blinking is common
        }

        // Factor 6: Tiny face size (0-1 relative coords)
        if (face.getBoundingBox() != null) {
            BoundingBox box = face.getBoundingBox();
            double faceArea = box.width() * box.height();
            if (faceArea < 0.005) { // <0.5% of image area is tiny
                spoofScore += 0.15;
            }
        }

        // Additive score capped at 1.0
        return Math.min(1.0, spoofScore);
    }

    public IndexFacesResponse indexFace(byte[] imageBytes, String externalId) {
        if (rekognitionClient == null) {
            log.warn("Attempted to index face but AWS Rekognition is not configured.");
            return null;
        }

        ensureCollectionExists();

        Image image = Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();

        try {
            IndexFacesRequest request = IndexFacesRequest.builder()
                    .collectionId(collectionId)
                    .image(image)
                    .externalImageId(externalId)
                    .maxFaces(1)
                    .qualityFilter(QualityFilter.AUTO)
                    .detectionAttributes(Attribute.ALL)
                    .build();

            return rekognitionClient.indexFaces(request);
        } catch (RekognitionException e) {
            log.error("AWS Rekognition Index Error: {}", e.getMessage());
            return null;
        }
    }

    private void ensureCollectionExists() {
        if (rekognitionClient == null)
            return;

        try {
            rekognitionClient.createCollection(CreateCollectionRequest.builder()
                    .collectionId(collectionId)
                    .build());
            log.info("Created AWS Rekognition collection: {}", collectionId);
        } catch (ResourceAlreadyExistsException e) {
            // Collection already exists, which is fine
        } catch (Exception e) {
            log.error("Failed to ensure AWS collection exists: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return rekognitionClient != null;
    }
}
