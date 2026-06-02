package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.AuthResponse;
import com.swp391.api.modules.user.dto.CustomerRegisterRequest;
import com.swp391.api.modules.user.dto.LoginRequest;

public interface AuthService {
    AuthResponse registerCustomer(CustomerRegisterRequest request);
    AuthResponse login(LoginRequest request);
}
