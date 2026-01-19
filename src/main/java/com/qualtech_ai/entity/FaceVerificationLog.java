package com.qualtech_ai.entity;

import com.qualtech_ai.enums.FaceProvider;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_verification_logs")
@Data
public class FaceVerificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id")
    private String userId; // Nullable, linked to FaceUser id if identified

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FaceProvider provider;

    @Column(name = "is_authorized")
    private boolean isAuthorized;

    @Column(name = "confidence_score")
    private double confidenceScore;

    @Column(name = "detected_emotion")
    private String detectedEmotion;

    @Column(name = "detected_age")
    private String detectedAge;

    @Column(name = "is_live")
    private boolean isLive;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
