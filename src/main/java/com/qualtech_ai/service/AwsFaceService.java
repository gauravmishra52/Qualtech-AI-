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

    public SearchFacesByImageResponse searchFace(byte[] imageBytes) {
        if (rekognitionClient == null) {
            log.warn("Attempted to search face but AWS Rekognition is not configured.");
            return null;
        }

        Image image = Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();

        try {
            SearchFacesByImageRequest request = SearchFacesByImageRequest.builder()
                    .collectionId(collectionId)
                    .image(image)
                    .maxFaces(1)
                    .faceMatchThreshold(80F)
                    .build();

            return rekognitionClient.searchFacesByImage(request);
        } catch (ResourceNotFoundException e) {
            log.error("Collection ID {} not found", collectionId);
            return null;
        } catch (RekognitionException e) {
            log.error("AWS Rekognition Error: {}", e.getMessage());
            throw e;
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
     */
    private double calculateSpoofProbability(AdvancedFaceDetail face) {
        double spoofScore = 0.0;
        int factors = 0;

        // Factor 1: Low quality (blurry images often indicate photos)
        if (face.getSharpness() != null && face.getSharpness() < 0.3) {
            spoofScore += 0.3;
            factors++;
        }

        // Factor 2: Extreme pose angles (unusual for live interaction)
        if (face.getPitch() != null && Math.abs(face.getPitch()) > 45) {
            spoofScore += 0.2;
            factors++;
        }
        if (face.getYaw() != null && Math.abs(face.getYaw()) > 45) {
            spoofScore += 0.2;
            factors++;
        }

        // Factor 3: High occlusion (might indicate photo edges)
        if (face.getOcclusionLevel() != null && face.getOcclusionLevel() > 0.5) {
            spoofScore += 0.25;
            factors++;
        }

        // Factor 4: Sunglasses (can hide spoof detection)
        if (face.getWearingSunglasses() != null && face.getWearingSunglasses()) {
            spoofScore += 0.15;
            factors++;
        }

        // Factor 5: Eyes closed (common in photos)
        if (face.getEyesOpen() != null && !face.getEyesOpen() && 
            face.getEyesOpenConfidence() != null && face.getEyesOpenConfidence() > 0.8) {
            spoofScore += 0.2;
            factors++;
        }

        // Factor 6: Very small face size
        if (face.getBoundingBox() != null) {
            BoundingBox box = face.getBoundingBox();
            double faceArea = box.width() * box.height();
            if (faceArea < 0.05) { // Very small faces are suspicious
                spoofScore += 0.3;
                factors++;
            }
        }

        // Normalize the score
        return factors > 0 ? spoofScore / factors : 0.0;
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
