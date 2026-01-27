package com.qualtech_ai.controller;

import com.qualtech_ai.dto.FaceRegistrationRequest;
import com.qualtech_ai.dto.FaceVerificationRequest;
import com.qualtech_ai.dto.FaceVerificationResponse;
import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.service.FaceRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/face")
@RequiredArgsConstructor
public class FaceRecognitionController {

    private final FaceRecognitionService faceRecognitionService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceUser> registerFace(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "position", required = false) String position,
            @RequestParam("image") MultipartFile image) throws IOException {

        // MANDATORY VALIDATIONS for face registration
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return ResponseEntity.badRequest().build();
        }

        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Image size ≥ 50 KB
        if (image.getSize() < 50 * 1024) {
            return ResponseEntity.badRequest().build();
        }

        // Basic image validation (more detailed validation in service layer)
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }

        FaceRegistrationRequest request = new FaceRegistrationRequest();
        request.setName(name.trim());
        request.setEmail(email.trim().toLowerCase());
        request.setDepartment(department);
        request.setPosition(position);
        request.setImage(image);

        FaceUser registeredUser = faceRecognitionService.registerFace(request);
        return new ResponseEntity<>(registeredUser, HttpStatus.CREATED);
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceVerificationResponse> verifyFace(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "provider", required = false, defaultValue = "LOCAL") com.qualtech_ai.enums.FaceProvider provider,
            @RequestParam(value = "live", required = false, defaultValue = "false") boolean live)
            throws IOException {

        FaceVerificationRequest request = new FaceVerificationRequest();
        request.setImage(image);
        request.setProvider(provider);
        request.setLive(live);

        FaceVerificationResponse response = faceRecognitionService.verifyFace(request);
        if ("Verification in progress".equals(response.getMessage())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<List<FaceUser>> getAllFaceUsers() {
        List<FaceUser> users = faceRecognitionService.getAllFaceUsers();
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteFaceUser(@PathVariable String id) {
        faceRecognitionService.deleteFaceUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processImage(@RequestParam("image") MultipartFile image) throws IOException {
        byte[] processedImage = faceRecognitionService.processImage(image);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/jpeg"))
                .body(processedImage);
    }

    @PostMapping(value = "/verify-stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceVerificationResponse> verifyFaceStream(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "provider", required = false, defaultValue = "LOCAL") com.qualtech_ai.enums.FaceProvider provider,
            @RequestParam(value = "live", required = false, defaultValue = "true") boolean live)
            throws IOException {

        // Stream mode - optimized for real-time performance
        FaceVerificationRequest request = new FaceVerificationRequest();
        request.setImage(image);
        request.setProvider(provider);
        request.setLive(live);

        FaceVerificationResponse response = faceRecognitionService.verifyFaceStream(request);
        if ("Verification in progress".equals(response.getMessage())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus() {
        return ResponseEntity.ok(faceRecognitionService.getSystemStatus());
    }
}
