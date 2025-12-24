package com.qualtech_ai.controller;

import com.qualtech_ai.model.ContactMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

    @PostMapping("/api/contact")
    @ResponseBody
    public ResponseEntity<?> handleContactForm(@RequestBody ContactMessage contactMessage) {
        try {
            // Log the received message
            logger.info("Received contact form submission: {}", contactMessage);
            
            // Here you would typically save the message to a database
            // For now, we'll just log it and return success
            
            return ResponseEntity.ok().body("{\"message\": \"Thank you for your message! We will get back to you soon.\"}");
        } catch (Exception e) {
            logger.error("Error processing contact form", e);
            return ResponseEntity.badRequest().body("{\"error\": \"Failed to process your request. Please try again later.\"}");
        }
    }

    // Keep existing endpoints for backward compatibility
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/index.html";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "forward:/index.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/index.html";
    }
}

