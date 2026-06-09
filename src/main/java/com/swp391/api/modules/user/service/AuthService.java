package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.AuthResponse;
import com.swp391.api.modules.user.dto.CustomerRegisterRequest;
import com.swp391.api.modules.user.dto.ForgotPasswordRequest;
import com.swp391.api.modules.user.dto.GoogleLoginRequest;
import com.swp391.api.modules.user.dto.LoginRequest;
import com.swp391.api.modules.user.dto.ResetPasswordRequest;
import com.swp391.api.modules.user.dto.VerifyOtpRequest;

public interface AuthService {
    AuthResponse registerCustomer(CustomerRegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse loginWithGoogle(GoogleLoginRequest request);
    String logout();
    String forgotPassword(ForgotPasswordRequest request);
    String resetPassword(ResetPasswordRequest request);
    String requestForgotPassword(String email);
    String resetPassword(String token, String newPassword);
    String sendOtp(String email);
    String verifyOtp(String email, String otp);
    String resetPasswordWithOtp(String email, String newPassword);
}
