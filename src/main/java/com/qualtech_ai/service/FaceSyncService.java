package com.qualtech_ai.service;

import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.repository.FaceUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.*;

/**
 * Service to synchronize face data between AWS Rekognition Collection and local
 * PostgreSQL database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceSyncService {

    private final FaceUserRepository faceUserRepository;
    private final RekognitionClient rekognitionClient;
    private static final String COLLECTION_ID = "qualtech-faces";

    /**
     * Audit and report sync discrepancies between AWS Rekognition and local DB
     * 
     * @return Sync report with detailed findings
     */
    @Transactional(readOnly = true)
    public SyncReport auditSync() {
        log.info("🔍 Starting AWS<->DB Sync Audit...");

        SyncReport report = new SyncReport();

        try {
            // 1. Get all faces from AWS Rekognition with PAGINATION FIX
            Map<String, Face> awsFacesById = new HashMap<>();
            Map<String, Face> awsFacesByExternalId = new HashMap<>();

            String nextToken = null;
            do {
                ListFacesRequest listRequest = ListFacesRequest.builder()
                        .collectionId(COLLECTION_ID)
                        .nextToken(nextToken)
                        .maxResults(1000)
                        .build();

                ListFacesResponse listResponse = rekognitionClient.listFaces(listRequest);

                for (Face face : listResponse.faces()) {
                    awsFacesById.put(face.faceId(), face);
                    if (face.externalImageId() != null && !face.externalImageId().isEmpty()) {
                        awsFacesByExternalId.put(face.externalImageId(), face);
                    }
                }
                
                nextToken = listResponse.nextToken();
            } while (nextToken != null);

            report.setTotalAwsFaces(awsFacesById.size());
            log.info("✅ Found {} faces in AWS Rekognition collection '{}'", awsFacesById.size(), COLLECTION_ID);

            // 2. Get all users from local DB
            List<FaceUser> dbUsers = faceUserRepository.findAll();
            report.setTotalDbUsers(dbUsers.size());
            log.info("✅ Found {} users in local database", dbUsers.size());

            // 3. Check each DB user against AWS
            for (FaceUser user : dbUsers) {
                String userId = user.getId();
                String awsFaceId = user.getAwsFaceId();

                // Check if user has AWS Face ID
                if (awsFaceId == null || awsFaceId.isEmpty()) {
                    report.addDbUserMissingAwsFaceId(user);
                    log.warn("⚠️  DB User '{}' (ID: {}) has NO aws_face_id", user.getName(), userId);
                } else {
                    // Verify AWS Face ID exists in AWS collection
                    if (!awsFacesById.containsKey(awsFaceId)) {
                        report.addDbUserWithInvalidAwsFaceId(user);
                        log.error("❌ DB User '{}' has aws_face_id='{}' BUT this face does NOT exist in AWS!",
                                user.getName(), awsFaceId);
                    } else {
                        report.addSyncedUser(user);
                        log.debug("✅ DB User '{}' correctly synced with AWS (FaceId: {})", user.getName(), awsFaceId);
                    }
                }
            }

            // 4. Check each AWS face against DB
            for (Face awsFace : awsFacesById.values()) {
                String faceId = awsFace.faceId();
                String externalId = awsFace.externalImageId();

                // Try to find corresponding DB user
                Optional<FaceUser> userByFaceId = faceUserRepository.findByAwsFaceId(faceId);
                Optional<FaceUser> userById = Optional.empty();

                if (userByFaceId.isEmpty()) {
                    // Try to find by externalId in BOTH externalImageId and primary id columns
                    if (externalId != null && !externalId.isEmpty()) {
                        userById = faceUserRepository.findByExternalImageId(externalId)
                                .or(() -> faceUserRepository.findById(externalId));
                    }
                }

                if (userByFaceId.isEmpty() && userById.isEmpty()) {
                    report.addOrphanedAwsFace(awsFace);
                    log.error("❌ AWS Face (FaceId: {}, ExternalId: {}) NOT FOUND in DB - ORPHANED!",
                            faceId, externalId != null ? externalId : "NULL");
                }
            }

            report.generateSummary();
            return report;

        } catch (Exception e) {
            log.error("🔥 Sync audit failed: {}", e.getMessage(), e);
            report.setErrorMessage(e.getMessage());
            return report;
        }
    }

    /**
     * Fix sync issues by updating aws_face_id in the database based on AWS
     * ExternalImageId
     */
    @Transactional
    public int fixSyncByExternalId() {
        log.info("🔧 Starting Sync Fix (ExternalId -> aws_face_id update)...");

        int fixedCount = 0;

        try {
            // Get all faces from AWS with PAGINATION FIX
            String nextToken = null;
            do {
                ListFacesRequest listRequest = ListFacesRequest.builder()
                        .collectionId(COLLECTION_ID)
                        .nextToken(nextToken)
                        .maxResults(1000)
                        .build();

                ListFacesResponse listResponse = rekognitionClient.listFaces(listRequest);

                for (Face awsFace : listResponse.faces()) {
                    String faceId = awsFace.faceId();
                    String externalId = awsFace.externalImageId();

                    if (externalId == null || externalId.isEmpty()) {
                        log.warn("⚠️  AWS Face {} has NO ExternalImageId, skipping", faceId);
                        continue;
                    }

                    // Find user by AWS ExternalImageId or Primary ID
                    Optional<FaceUser> userOpt = faceUserRepository.findByExternalImageId(externalId)
                            .or(() -> faceUserRepository.findById(externalId));

                    if (userOpt.isPresent()) {
                        FaceUser user = userOpt.get();

                        // Check if aws_face_id needs updating
                        if (user.getAwsFaceId() == null || !user.getAwsFaceId().equals(faceId)) {
                            log.info("🔧 Fixing User '{}' (ID: {}): Setting aws_face_id to '{}'",
                                    user.getName(), externalId, faceId);

                            user.setAwsFaceId(faceId);
                            faceUserRepository.save(user);
                            fixedCount++;
                        }
                    } else {
                        log.warn("⚠️  AWS Face has ExternalId='{}' but NO matching DB user found", externalId);
                    }
                }
                
                nextToken = listResponse.nextToken();
            } while (nextToken != null);

            log.info("✅ Sync fix complete: {} users updated", fixedCount);
            return fixedCount;

        } catch (Exception e) {
            log.error("🔥 Sync fix failed: {}", e.getMessage(), e);
            return fixedCount;
        }
    }

    /**
     * Delete orphaned faces from AWS Rekognition that don't exist in DB
     */
    @Transactional
    public int cleanOrphanedAwsFaces() {
        log.info("🗑️  Starting cleanup of orphaned AWS faces...");

        int deletedCount = 0;

        try {
            // Get all faces from AWS with PAGINATION FIX
            String nextToken = null;
            do {
                ListFacesRequest listRequest = ListFacesRequest.builder()
                        .collectionId(COLLECTION_ID)
                        .nextToken(nextToken)
                        .maxResults(1000)
                        .build();

                ListFacesResponse listResponse = rekognitionClient.listFaces(listRequest);
                List<String> faceIdsToDelete = new ArrayList<>();

                for (Face awsFace : listResponse.faces()) {
                    String faceId = awsFace.faceId();
                    String externalId = awsFace.externalImageId();

                    // Try to find corresponding DB user
                    Optional<FaceUser> userByFaceId = faceUserRepository.findByAwsFaceId(faceId);
                    Optional<FaceUser> userById = Optional.empty();

                    if (userByFaceId.isEmpty()) {
                        if (externalId != null && !externalId.isEmpty()) {
                            userById = faceUserRepository.findByExternalImageId(externalId)
                                    .or(() -> faceUserRepository.findById(externalId));
                        }
                    }

                    if (userByFaceId.isEmpty() && userById.isEmpty()) {
                        log.warn("🗑️  Orphaned AWS Face found: FaceId={}, ExternalId={}", faceId, externalId);
                        faceIdsToDelete.add(faceId);
                    }
                }

                // Delete orphaned faces from AWS if any found in this batch
                if (!faceIdsToDelete.isEmpty()) {
                    DeleteFacesRequest deleteRequest = DeleteFacesRequest.builder()
                            .collectionId(COLLECTION_ID)
                            .faceIds(faceIdsToDelete)
                            .build();

                    DeleteFacesResponse deleteResponse = rekognitionClient.deleteFaces(deleteRequest);
                    deletedCount += deleteResponse.deletedFaces().size();

                    log.info("✅ Deleted {} orphaned faces from AWS Rekognition (this batch)", deleteResponse.deletedFaces().size());
                }
                
                nextToken = listResponse.nextToken();
            } while (nextToken != null);

            return deletedCount;

        } catch (Exception e) {
            log.error("🔥 Cleanup failed: {}", e.getMessage(), e);
            return deletedCount;
        }
    }

    /**
     * Data class for sync audit report
     */
    public static class SyncReport {
        private int totalAwsFaces;
        private int totalDbUsers;
        private List<FaceUser> syncedUsers = new ArrayList<>();
        private List<FaceUser> dbUsersMissingAwsFaceId = new ArrayList<>();
        private List<FaceUser> dbUsersWithInvalidAwsFaceId = new ArrayList<>();
        private List<Face> orphanedAwsFaces = new ArrayList<>();
        private String errorMessage;
        private String summary;

        public void addSyncedUser(FaceUser user) {
            syncedUsers.add(user);
        }

        public void addDbUserMissingAwsFaceId(FaceUser user) {
            dbUsersMissingAwsFaceId.add(user);
        }

        public void addDbUserWithInvalidAwsFaceId(FaceUser user) {
            dbUsersWithInvalidAwsFaceId.add(user);
        }

        public void addOrphanedAwsFace(Face face) {
            orphanedAwsFaces.add(face);
        }

        public void generateSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== SYNC AUDIT REPORT ===\n");
            sb.append(String.format("AWS Faces: %d\n", totalAwsFaces));
            sb.append(String.format("DB Users: %d\n", totalDbUsers));
            sb.append(String.format("✅ Synced Users: %d\n", syncedUsers.size()));
            sb.append(String.format("⚠️  DB Users Missing aws_face_id: %d\n", dbUsersMissingAwsFaceId.size()));
            sb.append(String.format("❌ DB Users with Invalid aws_face_id: %d\n", dbUsersWithInvalidAwsFaceId.size()));
            sb.append(String.format("❌ Orphaned AWS Faces: %d\n", orphanedAwsFaces.size()));

            if (!orphanedAwsFaces.isEmpty()) {
                sb.append("\n🔥 CRITICAL: Orphaned AWS Faces Details:\n");
                for (Face face : orphanedAwsFaces) {
                    sb.append(String.format("  - FaceId: %s, ExternalId: %s\n",
                            face.faceId(), face.externalImageId()));
                }
            }

            this.summary = sb.toString();
        }

        // Getters and setters
        public int getTotalAwsFaces() {
            return totalAwsFaces;
        }

        public void setTotalAwsFaces(int totalAwsFaces) {
            this.totalAwsFaces = totalAwsFaces;
        }

        public int getTotalDbUsers() {
            return totalDbUsers;
        }

        public void setTotalDbUsers(int totalDbUsers) {
            this.totalDbUsers = totalDbUsers;
        }

        public List<FaceUser> getSyncedUsers() {
            return syncedUsers;
        }

        public List<FaceUser> getDbUsersMissingAwsFaceId() {
            return dbUsersMissingAwsFaceId;
        }

        public List<FaceUser> getDbUsersWithInvalidAwsFaceId() {
            return dbUsersWithInvalidAwsFaceId;
        }

        public List<Face> getOrphanedAwsFaces() {
            return orphanedAwsFaces;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getSummary() {
            return summary;
        }

        @Override
        public String toString() {
            return summary != null ? summary : "No summary generated";
        }
    }
}
