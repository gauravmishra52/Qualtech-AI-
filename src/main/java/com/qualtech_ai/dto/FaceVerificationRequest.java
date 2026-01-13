package com.qualtech_ai.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FaceVerificationRequest {
    private MultipartFile image;
}
