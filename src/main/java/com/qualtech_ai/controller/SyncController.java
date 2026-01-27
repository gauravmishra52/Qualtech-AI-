package com.qualtech_ai.controller;

import com.qualtech_ai.service.FaceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for database and AWS synchronization operations
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SyncController {

    private final FaceSyncService faceSyncService;

    /**
     * Audit sync status between AWS Rekognition and local database
     * GET /api/admin/sync/audit
     */
    @GetMapping("/audit")
    public ResponseEntity<FaceSyncService.SyncReport> auditSync() {
        log.info("📊 Admin requested sync audit");
        try {
            FaceSyncService.SyncReport report = faceSyncService.auditSync();
            log.info("✅ Sync audit completed:\n{}", report);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("❌ Sync audit failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Fix sync issues by updating aws_face_id fields in the database
     * POST /api/admin/sync/fix
     */
    @PostMapping("/fix")
    public ResponseEntity<Map<String, Object>> fixSync() {
        log.info("🔧 Admin requested sync fix");
        try {
            int fixedCount = faceSyncService.fixSyncByExternalId();
            log.info("✅ Sync fix completed: {} users updated", fixedCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fixedCount", fixedCount,
                    "message", String.format("Successfully updated %d users with correct AWS Face IDs", fixedCount)));
        } catch (Exception e) {
            log.error("❌ Sync fix failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Clean orphaned faces from AWS Rekognition that don't exist in the database
     * DELETE /api/admin/sync/orphaned
     */
    @DeleteMapping("/orphaned")
    public ResponseEntity<Map<String, Object>> cleanOrphaned() {
        log.info("🗑️  Admin requested cleanup of orphaned AWS faces");
        try {
            int deletedCount = faceSyncService.cleanOrphanedAwsFaces();
            log.info("✅ Cleanup completed: {} orphaned faces removed from AWS", deletedCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deletedCount", deletedCount,
                    "message",
                    String.format("Successfully deleted %d orphaned faces from AWS Rekognition", deletedCount)));
        } catch (Exception e) {
            log.error("❌ Cleanup failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Complete sync recovery: Audit, Fix, and Clean in one operation
     * POST /api/admin/sync/recover
     */
    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recoverSync() {
        log.info("🚑 Admin requested complete sync recovery");
        try {
            // Step 1: Audit
            FaceSyncService.SyncReport auditReport = faceSyncService.auditSync();
            log.info("📊 Audit: {}", auditReport.getSummary());

            // Step 2: Fix
            int fixedCount = faceSyncService.fixSyncByExternalId();
            log.info("🔧 Fixed: {} users", fixedCount);

            // Step 3: Clean
            int deletedCount = faceSyncService.cleanOrphanedAwsFaces();
            log.info("🗑️  Cleaned: {} orphaned faces", deletedCount);

            // Step 4: Re-audit to confirm
            FaceSyncService.SyncReport finalReport = faceSyncService.auditSync();
            log.info("✅ Final Audit: {}", finalReport.getSummary());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fixedCount", fixedCount,
                    "deletedCount", deletedCount,
                    "initialAudit", auditReport.getSummary(),
                    "finalAudit", finalReport.getSummary(),
                    "message", "Sync recovery completed successfully"));
        } catch (Exception e) {
            log.error("❌ Sync recovery failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }
}
