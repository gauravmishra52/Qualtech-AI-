package com.qualtech_ai.service;

import com.qualtech_ai.dto.AuthResponse;
import com.qualtech_ai.dto.LoginRequest;
import com.qualtech_ai.dto.RegisterRequest;
import com.qualtech_ai.dto.ResetPasswordRequest;
import org.springframework.transaction.annotation.Transactional;

public interface AuthService {
    @Transactional
    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    @Transactional
    void resetPassword(ResetPasswordRequest request);

    @Transactional
    void sendResetToken(String email);

    String generateToken(String email);

    @Transactional
    void verifyEmail(String email, String otp);
}
