package com.qualtech_ai.service;

import com.azure.ai.vision.face.FaceClient;
import com.azure.ai.vision.face.models.*;
import com.azure.core.util.BinaryData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AzureFaceService {

    private final FaceClient faceClient;

    public AzureFaceService(Optional<FaceClient> faceClient) {
        this.faceClient = faceClient.orElse(null);
        if (this.faceClient == null) {
            log.warn("Azure Face Client is not configured. Azure features will be disabled.");
        }
    }

    /**
     * Detect faces in an image byte array
     */
    public List<FaceDetectionResult> detectFaces(byte[] imageBytes) {
        if (faceClient == null) {
            log.warn("Attempted to detect faces but Azure Face Client is not configured.");
            return new ArrayList<>();
        }

        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("Image bytes are null or empty, cannot detect faces.");
            return new ArrayList<>();
        }

        // Check for minimum image size (Azure Face API has limits)
        if (imageBytes.length < 1024) {
            log.warn("Image is too small ({} bytes) for face detection.", imageBytes.length);
            return new ArrayList<>();
        }

        try {
            log.debug("Sending detection request to Azure with {} bytes", imageBytes.length);
            // Request face attributes including age
            List<FaceAttributeType> attributes = new ArrayList<>();
            attributes.add(FaceAttributeType.AGE);

            List<FaceDetectionResult> azureResults = faceClient.detect(
                    BinaryData.fromBytes(imageBytes),
                    FaceDetectionModel.DETECTION_03,
                    FaceRecognitionModel.RECOGNITION_04,
                    true, // returnFaceId
                    attributes, // Request age attribute
                    false, // returnFaceLandmarks
                    true, // returnFaceAttributes - ENABLE THIS
                    null); // recognition model quality

            log.debug("Azure returned {} face(s)", azureResults != null ? azureResults.size() : "null");
            return azureResults != null ? azureResults : new ArrayList<>();
        } catch (Exception e) {
            log.error("Azure Face Detection Failed: {}", e.getMessage(), e);
            // Return empty list instead of throwing to maintain graceful degradation
            return new ArrayList<>();
        }
    }

    public boolean isAvailable() {
        return faceClient != null;
    }
}
