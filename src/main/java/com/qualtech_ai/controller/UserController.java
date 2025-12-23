package com.qualtech_ai.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

  
    @GetMapping("/profile")
    public String getProfile() {
        return "This is a secured user profile";
    }
}

