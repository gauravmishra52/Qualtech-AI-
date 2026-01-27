package com.qualtech_ai.service;

import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.repository.FaceUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service to perform startup integrity checks for face recognition system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartupIntegrityService {

    private final FaceUserRepository faceUserRepository;
    private final RekognitionClient rekognitionClient;
    private static final String COLLECTION_ID = "qualtech-faces";

    @PostConstruct
    public void performStartupIntegrityCheck() {
        log.info("🔍 Starting startup integrity checks...");
        
        try {
            checkDuplicateAwsFaceIds();
            checkDuplicateExternalImageIds();
            checkNullEmbeddings();
            checkOrphanedAwsFaces();
            
            log.info("✅ Startup integrity checks completed successfully");
        } catch (Exception e) {
            log.error("🔥 Startup integrity checks failed: {}", e.getMessage(), e);
            // Don't throw exception to allow application startup
        }
    }

    private void checkDuplicateAwsFaceIds() {
        log.info("🔍 Checking for duplicate awsFaceId in database...");
        
        List<FaceUser> allUsers = faceUserRepository.findAll();
        Map<String, List<FaceUser>> awsFaceIdMap = new HashMap<>();
        
        for (FaceUser user : allUsers) {
            if (user.getAwsFaceId() != null && !user.getAwsFaceId().isEmpty()) {
                awsFaceIdMap.computeIfAbsent(user.getAwsFaceId(), k -> new ArrayList<>()).add(user);
            }
        }
        
        int duplicateCount = 0;
        for (Map.Entry<String, List<FaceUser>> entry : awsFaceIdMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicateCount++;
                log.warn("⚠️  DUPLICATE awsFaceId FOUND: {} -> {} users", 
                    entry.getKey(), entry.getValue().size());
                for (FaceUser user : entry.getValue()) {
                    log.warn("   - User: {} (ID: {})", user.getEmail(), user.getId());
                }
            }
        }
        
        if (duplicateCount == 0) {
            log.info("✅ No duplicate awsFaceId found");
        } else {
            log.error("❌ Found {} duplicate awsFaceId entries", duplicateCount);
        }
    }

    private void checkDuplicateExternalImageIds() {
        log.info("🔍 Checking for duplicate externalImageId in database...");
        
        List<FaceUser> allUsers = faceUserRepository.findAll();
        Map<String, List<FaceUser>> externalImageIdMap = new HashMap<>();
        
        for (FaceUser user : allUsers) {
            if (user.getExternalImageId() != null && !user.getExternalImageId().isEmpty()) {
                externalImageIdMap.computeIfAbsent(user.getExternalImageId(), k -> new ArrayList<>()).add(user);
            }
        }
        
        int duplicateCount = 0;
        for (Map.Entry<String, List<FaceUser>> entry : externalImageIdMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicateCount++;
                log.warn("⚠️  DUPLICATE externalImageId FOUND: {} -> {} users", 
                    entry.getKey(), entry.getValue().size());
                for (FaceUser user : entry.getValue()) {
                    log.warn("   - User: {} (ID: {})", user.getEmail(), user.getId());
                }
            }
        }
        
        if (duplicateCount == 0) {
            log.info("✅ No duplicate externalImageId found");
        } else {
            log.error("❌ Found {} duplicate externalImageId entries", duplicateCount);
        }
    }

    private void checkNullEmbeddings() {
        log.info("🔍 Checking for null embeddings in database...");
        
        List<FaceUser> allUsers = faceUserRepository.findAll();
        int nullEmbeddingCount = 0;
        
        for (FaceUser user : allUsers) {
            if (user.getFaceEmbedding() == null || user.getFaceEmbedding().trim().isEmpty()) {
                nullEmbeddingCount++;
                log.warn("⚠️  NULL EMBEDDING FOUND: User {} (ID: {})", user.getEmail(), user.getId());
            }
        }
        
        if (nullEmbeddingCount == 0) {
            log.info("✅ No null embeddings found");
        } else {
            log.error("❌ Found {} users with null embeddings", nullEmbeddingCount);
        }
    }

    private void checkOrphanedAwsFaces() {
        log.info("🔍 Checking for orphaned AWS faces...");
        
        try {
            // Get all faces from AWS with pagination
            Map<String, Face> awsFacesById = new HashMap<>();
            String nextToken = null;
            int totalAwsFaces = 0;
            
            do {
                ListFacesRequest listRequest = ListFacesRequest.builder()
                        .collectionId(COLLECTION_ID)
                        .nextToken(nextToken)
                        .maxResults(1000)
                        .build();

                ListFacesResponse listResponse = rekognitionClient.listFaces(listRequest);
                
                for (Face face : listResponse.faces()) {
                    awsFacesById.put(face.faceId(), face);
                    totalAwsFaces++;
                }
                
                nextToken = listResponse.nextToken();
            } while (nextToken != null);

            log.info("📊 Found {} faces in AWS collection", totalAwsFaces);

            // Get all users from DB
            List<FaceUser> allUsers = faceUserRepository.findAll();
            Set<String> dbAwsFaceIds = new HashSet<>();
            
            for (FaceUser user : allUsers) {
                if (user.getAwsFaceId() != null && !user.getAwsFaceId().isEmpty()) {
                    dbAwsFaceIds.add(user.getAwsFaceId());
                }
            }

            // Find orphaned faces
            int orphanedCount = 0;
            for (String awsFaceId : awsFacesById.keySet()) {
                if (!dbAwsFaceIds.contains(awsFaceId)) {
                    orphanedCount++;
                    Face orphanedFace = awsFacesById.get(awsFaceId);
                    log.warn("🗑️  ORPHANED AWS FACE: FaceId={}, ExternalId={}", 
                        awsFaceId, orphanedFace.externalImageId());
                }
            }

            if (orphanedCount == 0) {
                log.info("✅ No orphaned AWS faces found");
            } else {
                log.error("❌ Found {} orphaned AWS faces (run cleanup to remove)", orphanedCount);
            }

        } catch (Exception e) {
            log.error("🔥 Failed to check orphaned AWS faces: {}", e.getMessage());
        }
    }
}
