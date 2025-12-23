package com.qualtech_ai.service.impl;

import com.qualtech_ai.entity.User;
import com.qualtech_ai.exception.CustomException;
import com.qualtech_ai.repository.UserRepository;
import com.qualtech_ai.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new CustomException("User not found"));
    }
}

