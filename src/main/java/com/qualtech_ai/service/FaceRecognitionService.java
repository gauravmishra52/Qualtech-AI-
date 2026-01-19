package com.qualtech_ai.service;

import com.qualtech_ai.dto.FaceRegistrationRequest;
import com.qualtech_ai.dto.FaceVerificationRequest;
import com.qualtech_ai.dto.FaceVerificationResponse;
import com.qualtech_ai.entity.FaceUser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface FaceRecognitionService {
    /**
     * Register a new face for authentication
     * @param request The face registration request containing user details and image
     * @return The registered FaceUser
     */
    FaceUser registerFace(FaceRegistrationRequest request) throws IOException;
    
    /**
     * Verify a face against registered users
     * @param request The verification request containing the face image
     * @return Verification response with match details
     */
    FaceVerificationResponse verifyFace(FaceVerificationRequest request) throws IOException;
    
    /**
     * Verify a face in stream mode - optimized for real-time performance
     * @param request The verification request containing the face image
     * @return Fast verification response with match details
     */
    FaceVerificationResponse verifyFaceStream(FaceVerificationRequest request) throws IOException;
    
    /**
     * Get all registered face users
     * @return List of all registered users
     */
    List<FaceUser> getAllFaceUsers();
    
    /**
     * Delete a face user by ID
     * @param id The ID of the user to delete
     */
    void deleteFaceUser(String id);
    
    /**
     * Process an image for face detection
     * @param imageFile The image file to process
     * @return The processed image with face detection
     */
    byte[] processImage(MultipartFile imageFile) throws IOException;
    
    /**
     * Get system status including model loading state
     * @return System status information
     */
    Map<String, Object> getSystemStatus();
}
