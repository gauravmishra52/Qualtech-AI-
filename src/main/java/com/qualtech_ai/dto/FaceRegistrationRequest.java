package com.qualtech_ai.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FaceRegistrationRequest {
    private String name;
    private String email;
    private String department;
    private String position;
    private MultipartFile image;
}
