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

        FaceRegistrationRequest request = new FaceRegistrationRequest();
        request.setName(name);
        request.setEmail(email);
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
                .contentType(MediaType.IMAGE_JPEG)
                .body(processedImage);
    }
}
