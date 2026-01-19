package com.qualtech_ai.repository;

import com.qualtech_ai.entity.FaceVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaceVerificationLogRepository extends JpaRepository<FaceVerificationLog, String> {
    List<FaceVerificationLog> findByUserIdOrderByTimestampDesc(String userId);
}
