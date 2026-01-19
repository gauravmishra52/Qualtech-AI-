package com.qualtech_ai.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FaceVerificationRequest {
    private MultipartFile image;
    private com.qualtech_ai.enums.FaceProvider provider; // AWS, AZURE, LOCAL
    private boolean live = false;
}
