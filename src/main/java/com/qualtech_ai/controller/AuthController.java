package com.qualtech_ai.controller;

import com.qualtech_ai.dto.AuthResponse;
import com.qualtech_ai.dto.LoginRequest;
import com.qualtech_ai.dto.RefreshTokenRequest;
import com.qualtech_ai.dto.RegisterRequest;
import com.qualtech_ai.entity.RefreshToken;
import com.qualtech_ai.exception.TokenRefreshException;
import com.qualtech_ai.service.AuthService;
import com.qualtech_ai.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            logger.info("Attempting to register user: {}", request.getEmail());
            authService.register(request);
            logger.info("User registered successfully: {}", request.getEmail());
            Map<String, String> response = new HashMap<>();
            response.put("message", "User registered successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Registration failed for user: " + request.getEmail(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            logger.info("Login attempt for user: {}", request.getEmail());
            AuthResponse response = authService.login(request);
            logger.info("Login successful for user: {}", request.getEmail());

            // Return token in both header and body
            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.getToken())
                    .body(response);
        } catch (Exception e) {
            logger.error("Login failed for user: " + request.getEmail(), e);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            logger.info("Refreshing token");

            return refreshTokenService.findByToken(refreshToken)
                    .map(refreshTokenService::verifyExpiration)
                    .map(RefreshToken::getUser)
                    .map(user -> {
                        String token = authService.generateToken(user.getEmail());
                        Set<String> roleNames = user.getRoles().stream()
                                .map(role -> role.getName().startsWith("ROLE_") ? role.getName()
                                        : "ROLE_" + role.getName())
                                .collect(java.util.stream.Collectors.toSet());

                        logger.info("Token refreshed successfully for user: {}", user.getEmail());
                        return ResponseEntity.ok(new AuthResponse(
                                token,
                                refreshToken,
                                "Bearer",
                                user.getId(),
                                user.getEmail(),
                                user.getEmail(),
                                roleNames));
                    })
                    .orElseThrow(() -> {
                        logger.error("Refresh token not found or expired: {}", refreshToken);
                        return new TokenRefreshException(refreshToken, "Refresh token is not in database or expired");
                    });
        } catch (Exception e) {
            logger.error("Token refresh failed", e);
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", "Token refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            logger.info("Logout request received");
            refreshTokenService.revokeToken(request.getRefreshToken());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Logout successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Logout failed", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Logout failed: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        try {
            logger.info("Attempting to verify email for: {}", email);
            authService.verifyEmail(email, otp);
            logger.info("Email verified successfully");
            Map<String, String> response = new HashMap<>();
            response.put("message", "Email verified successfully. You can now log in.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Email verification failed", e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", "Verification failed: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            logger.info("Password reset requested for email: {}", email);
            authService.sendResetToken(email);
            return ResponseEntity.ok(Collections.singletonMap("message", "Password reset link sent to your email"));
        } catch (Exception e) {
            logger.error("Forgot password request failed", e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody com.qualtech_ai.dto.ResetPasswordRequest request) {
        try {
            logger.info("Setting new password");
            authService.resetPassword(request);
            return ResponseEntity.ok(Collections.singletonMap("message", "Password reset successfully"));
        } catch (Exception e) {
            logger.error("Password reset failed", e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}