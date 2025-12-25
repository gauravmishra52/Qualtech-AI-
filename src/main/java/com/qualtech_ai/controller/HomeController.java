package com.qualtech_ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.qualtech_ai.dto.ContactMessage;

/**
 * REST Controller for general endpoints.
 * 
 * NOTE: For SPA routing, we don't need explicit route handlers here.
 * Spring Boot automatically serves static/index.html for "/".
 * The SPA's client-side router handles routes like /login, /register,
 * /dashboard.
 */
@RestController
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @PostMapping("/api/contact")
    public ResponseEntity<?> handleContactForm(@RequestBody ContactMessage contactMessage) {
        try {
            // Log the received message
            logger.info("Received contact form submission: {}", contactMessage);

            // Here you would typically save the message to a database
            // For now, we'll just log it and return success

            return ResponseEntity.ok()
                    .body("{\"message\": \"Thank you for your message! We will get back to you soon.\"}");
        } catch (Exception e) {
            logger.error("Error processing contact form", e);
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Failed to process your request. Please try again later.\"}");
        }
    }
}
