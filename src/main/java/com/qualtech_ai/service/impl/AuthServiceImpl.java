package com.qualtech_ai.service.impl;

import com.qualtech_ai.dto.*;
import com.qualtech_ai.entity.PasswordResetToken;
import com.qualtech_ai.entity.Role;
import com.qualtech_ai.entity.User;
import com.qualtech_ai.exception.CustomException;
import com.qualtech_ai.repository.PasswordResetTokenRepository;
import com.qualtech_ai.repository.RoleRepository;
import com.qualtech_ai.repository.UserRepository;
import com.qualtech_ai.security.JwtUtil;
import com.qualtech_ai.service.AuthService;
import com.qualtech_ai.service.EmailService;
import com.qualtech_ai.util.DateUtil;
import com.qualtech_ai.util.EmailUtil;
import com.qualtech_ai.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_USER_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.email.verification.token-expiration:86400000}")
    private long tokenExpirationMs;

    // ================= REGISTER =================
    @Override
    @Transactional
    public void register(RegisterRequest request) {

        if (request == null ||
                !StringUtils.hasText(request.getEmail()) ||
                !StringUtils.hasText(request.getPassword())) {
            throw new CustomException("Invalid registration request");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException("Email already exists");
        }

        Role role = roleRepository.findByName(DEFAULT_USER_ROLE)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(DEFAULT_USER_ROLE);
                    return roleRepository.save(newRole);
                });

        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(role));
        user.setEnabled(false);

        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        user.setVerificationToken(otp);
        user.setVerificationTokenExpiry(
                LocalDateTime.now().plus(Duration.ofMinutes(15))); // OTP valid for 15 minutes

        userRepository.save(user);

        emailService.sendEmail(
                user.getEmail(),
                "Qualtech AI - Verify Your Email",
                EmailUtil.verificationEmailBody(user.getUsername(), otp));
    }

    // ================= LOGIN =================
    @Override
    public AuthResponse login(LoginRequest request) {

        if (request == null ||
                !StringUtils.hasText(request.getEmail()) ||
                !StringUtils.hasText(request.getPassword())) {
            throw new CustomException("Email and password are required");
        }

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new CustomException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new CustomException("Please verify your email before logging in");
        }

        Map<String, String> tokens = jwtUtil.generateTokens(user.getEmail());
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new AuthResponse(
                tokens.get("accessToken"),
                tokens.get("refreshToken"),
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roles);
    }

    // ================= VERIFY EMAIL =================
    @Override
    @Transactional
    public void verifyEmail(String email, String otp) {
        log.info("Starting email verification for email: {}", email);

        if (!StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
            throw new CustomException("Email and OTP are required");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new CustomException("User not found"));

        if (!otp.equals(user.getVerificationToken())) {
            throw new CustomException("Invalid OTP");
        }

        if (user.isEnabled()) {
            log.info("Email already verified for user: {}", user.getEmail());
            throw new CustomException("Email already verified");
        }

        if (user.getVerificationTokenExpiry() != null &&
                user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Verification failed: Token expired for user: {}", user.getEmail());
            throw new CustomException("Verification token expired");
        }

        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("Email verified successfully for user: {}", user.getEmail());

        try {
            emailService.sendEmail(
                    user.getEmail(),
                    "Qualtech AI - Registration Confirmed",
                    "Your email has been verified successfully. You can now log in.");
            log.info("Confirmation email sent to user: {}", user.getEmail());
        } catch (Exception e) {
            log.warn("Confirmation email failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ================= PASSWORD RESET =================
    @Override
    @Transactional
    public void sendResetToken(String email) {

        if (!StringUtils.hasText(email)) {
            throw new CustomException("Email is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found"));

        tokenRepository.deleteByUser(user);

        String token = TokenGenerator.generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(DateUtil.calculateExpiryDate(24 * 60));
        tokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        log.info("Preparing to send reset email to {} with link: {}", user.getEmail(), resetLink);

        emailService.sendEmail(
                user.getEmail(),
                "Password Reset Request",
                EmailUtil.resetPasswordEmailBody(token));
        log.info("Finished call to emailService for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        if (request == null || !StringUtils.hasText(request.getToken()) ||
                !StringUtils.hasText(request.getNewPassword())) {
            throw new CustomException("Token and new password are required");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new CustomException("Invalid or expired token"));

        if (DateUtil.isExpired(resetToken.getExpiryDate())) {
            tokenRepository.delete(resetToken);
            throw new CustomException("Token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        try {
            userRepository.save(user);
            tokenRepository.delete(resetToken);
            log.info("Password reset successful for: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage());
            throw new CustomException("Error resetting password");
        }
    }

    @Override
    public String generateToken(String email) {
        return jwtUtil.generateToken(email);
    }
}
