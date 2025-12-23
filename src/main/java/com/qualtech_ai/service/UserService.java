package com.qualtech_ai.service;

import com.qualtech_ai.entity.User;

public interface UserService {

    User findByEmail(String email);
}
