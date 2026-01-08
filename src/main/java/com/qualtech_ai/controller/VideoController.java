package com.qualtech_ai.controller;

import com.qualtech_ai.dto.VideoResult;
import com.qualtech_ai.service.VideoAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/video")
@CrossOrigin(origins = "*")
public class VideoController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VideoController.class);
    private final VideoAnalysisService videoAnalysisService;

    public VideoController(VideoAnalysisService videoAnalysisService) {
        this.videoAnalysisService = videoAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeVideo(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "provider", required = false) String requestedProvider) {
        log.info("Received analysis request for file: {} using provider: {}", file.getOriginalFilename(),
                requestedProvider);
        try {
            VideoResult result = videoAnalysisService.analyzeVideo(file, requestedProvider);
            return ResponseEntity.ok(result);
        } catch (IOException | InterruptedException e) {
            log.error("Analysis failed for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body("Error analyzing video: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Internal service error for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body("Internal Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body("Unexpected error occurred: " + e.getMessage());
        }
    }
}
