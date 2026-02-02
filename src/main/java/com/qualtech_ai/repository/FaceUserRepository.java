package com.qualtech_ai.repository;

import com.qualtech_ai.entity.FaceUser;
import com.qualtech_ai.projection.FaceEmbeddingView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public interface FaceUserRepository extends JpaRepository<FaceUser, String> {
    Optional<FaceUser> findByEmail(String email);

    boolean existsByEmail(String email);

    // Critical: Lookup user by AWS Rekognition Face ID (NOT primary key!)
    Optional<FaceUser> findByAwsFaceId(String awsFaceId);

    // Critical: Lookup user by AWS External Image ID
    Optional<FaceUser> findByExternalImageId(String externalImageId);

    // Custom query to find user without loading LOB data (imageData)
    @Query("SELECT fu FROM FaceUser fu WHERE fu.externalImageId = :externalImageId")
    Optional<FaceUser> findByExternalImageIdWithoutImageData(String externalImageId);

    // Custom query to find user without loading LOB data (imageData)
    @Query("SELECT fu FROM FaceUser fu WHERE fu.awsFaceId = :awsFaceId")
    Optional<FaceUser> findByAwsFaceIdWithoutImageData(String awsFaceId);

    // Efficient duplicate check query - only retrieves face embeddings, no LOB data
    @Query("SELECT u.faceEmbedding FROM FaceUser u WHERE u.faceEmbedding IS NOT NULL")
    List<FaceEmbeddingView> findAllEmbeddings();
}
