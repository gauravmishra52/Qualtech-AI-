package com.qualtech_ai.repository;

import com.qualtech_ai.entity.FaceUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceUserRepository extends JpaRepository<FaceUser, String> {
    Optional<FaceUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
