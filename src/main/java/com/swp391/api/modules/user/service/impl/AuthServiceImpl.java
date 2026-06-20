package com.swp391.api.modules.user.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Random;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.swp391.api.common.security.JwtUtils;
import com.swp391.api.modules.user.dto.AuthResponse;
import com.swp391.api.modules.user.dto.CustomerRegisterRequest;
import com.swp391.api.modules.user.dto.ForgotPasswordRequest;
import com.swp391.api.modules.user.dto.GoogleLoginRequest;
import com.swp391.api.modules.user.dto.LoginRequest;
import com.swp391.api.modules.user.dto.ResetPasswordRequest;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.AuthService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final String GOOGLE_CLIENT_ID = "150115632028-8eqn9u4mse09dm8vj2dcquev3gp6vruq.apps.googleusercontent.com";

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final JavaMailSender javaMailSender;
    private final String frontendBaseUrl;

    public AuthServiceImpl(CustomerRepository customerRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           JavaMailSender javaMailSender,
                           @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
                           @Value("${jwt.secret}") String ignoredJwtSecret) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.javaMailSender = javaMailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(java.util.List.of(GOOGLE_CLIENT_ID))
                .build();
    }

    @Override
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {
        customerRepository.findByCustomersEmail(request.getCustomersEmail())
                .ifPresent(customer -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer email already exists");
                });

        Customer customer = new Customer();
        customer.setFullName(request.getFullName());
        customer.setCustomersEmail(request.getCustomersEmail());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setPhone(request.getPhone());
        customer.setAvatarUrl(request.getAvatarUrl());

        Customer saved = customerRepository.save(customer);
        String token = jwtUtils.generateToken(saved.getCustomersEmail(), "CUSTOMER");

        return new AuthResponse(token, "CUSTOMER", saved.getFullName(), saved.getCustomersEmail());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByCustomersEmail(request.getEmail()).orElse(null);
        if (customer != null) {
            if (!passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
            }
            String token = jwtUtils.generateToken(customer.getCustomersEmail(), "CUSTOMER");
            return new AuthResponse(token, "CUSTOMER", customer.getFullName(), customer.getCustomersEmail());
        }

        User user = userRepository.findByUserEmail(request.getEmail()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
            }
            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
            }
            String role = user.getRole();
            String token = jwtUtils.generateToken(user.getUserEmail(), role);
            return new AuthResponse(token, role, user.getFullName(), user.getUserEmail());
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email");
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(request.getCredentialToken());
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google credential token");
            }

            Payload payload = idToken.getPayload();
            String googleEmail = payload.getEmail();
            String googleName = (String) payload.get("name");
            String googleAvatar = (String) payload.get("picture");

            User user = userRepository.findByUserEmail(googleEmail).orElse(null);
            if (user != null) {
                if (Boolean.FALSE.equals(user.getIsActive())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
                }
                String token = jwtUtils.generateToken(user.getUserEmail(), user.getRole());
                return new AuthResponse(token, user.getRole(), user.getFullName(), user.getUserEmail());
            }

            Customer customer = customerRepository.findByCustomersEmail(googleEmail).orElse(null);
            if (customer == null) {
                Customer newCustomer = new Customer();
                newCustomer.setCustomersEmail(googleEmail);
                newCustomer.setFullName(googleName);
                newCustomer.setAvatarUrl(googleAvatar);
                newCustomer.setPhone(null);
                newCustomer.setPassword(passwordEncoder.encode(generateRandomPassword()));
                customer = customerRepository.save(newCustomer);
            }

            String token = jwtUtils.generateToken(customer.getCustomersEmail(), "CUSTOMER");
            return new AuthResponse(token, "CUSTOMER", customer.getFullName(), customer.getCustomersEmail());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login failed");
        }
    }

    @Override
    public String logout() {
        return "Logout successful";
    }

    @Override
    public String forgotPassword(ForgotPasswordRequest request) {
        return sendOtp(request.getEmail());
    }

    @Override
    public String resetPassword(ResetPasswordRequest request) {
        return resetPasswordWithOtp(request.getEmail(), request.getNewPassword());
    }

    @Override
    public String requestForgotPassword(String email) {
        return sendOtp(email);
    }

    @Override
    public String resetPassword(String token, String newPassword) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use OTP flow: verify OTP first, then reset password");
    }

    @Override
    public String sendOtp(String email) {
        User user = userRepository.findByUserEmail(email).orElse(null);
        if (user != null) {
            return createAndSendOtpForUser(user, email);
        }

        Customer customer = customerRepository.findByCustomersEmail(email).orElse(null);
        if (customer != null) {
            return createAndSendOtpForCustomer(customer, email);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    @Override
    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByUserEmail(email).orElse(null);
        if (user != null) {
            return verifyOtpForUser(user, otp);
        }

        Customer customer = customerRepository.findByCustomersEmail(email).orElse(null);
        if (customer != null) {
            return verifyOtpForCustomer(customer, otp);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    @Override
    public String resetPasswordWithOtp(String email, String newPassword) {
        User user = userRepository.findByUserEmail(email).orElse(null);
        if (user != null) {
            return resetUserPasswordAfterOtp(user, newPassword);
        }

        Customer customer = customerRepository.findByCustomersEmail(email).orElse(null);
        if (customer != null) {
            return resetCustomerPasswordAfterOtp(customer, newPassword);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    private String createAndSendOtpForUser(User user, String email) {
        String otp = generateOtp();
        user.setResetToken(otp);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(5));
        user.setResetTokenVerified(Boolean.FALSE);
        userRepository.save(user);
        sendOtpEmail(email, otp);
        return "OTP has been sent to your email.";
    }

    private String createAndSendOtpForCustomer(Customer customer, String email) {
        String otp = generateOtp();
        customer.setResetToken(otp);
        customer.setResetTokenExpiry(LocalDateTime.now().plusMinutes(5));
        customer.setResetTokenVerified(Boolean.FALSE);
        customerRepository.save(customer);
        sendOtpEmail(email, otp);
        return "OTP has been sent to your email.";
    }

    private String verifyOtpForUser(User user, String otp) {
        if (user.getResetToken() == null || user.getResetTokenExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not requested");
        }
        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        if (!user.getResetToken().equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }
        user.setResetTokenVerified(Boolean.TRUE);
        userRepository.save(user);
        return "OTP verified successfully.";
    }

    private String verifyOtpForCustomer(Customer customer, String otp) {
        if (customer.getResetToken() == null || customer.getResetTokenExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not requested");
        }
        if (customer.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        if (!customer.getResetToken().equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }
        customer.setResetTokenVerified(Boolean.TRUE);
        customerRepository.save(customer);
        return "OTP verified successfully.";
    }

    private String resetUserPasswordAfterOtp(User user, String newPassword) {
        if (!Boolean.TRUE.equals(user.getResetTokenVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not verified");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setResetTokenVerified(Boolean.FALSE);
        userRepository.save(user);
        return "Password reset successfully.";
    }

    private String resetCustomerPasswordAfterOtp(Customer customer, String newPassword) {
        if (!Boolean.TRUE.equals(customer.getResetTokenVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not verified");
        }
        if (customer.getResetToken() == null || customer.getResetTokenExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not verified");
        }
        if (customer.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        customer.setPassword(passwordEncoder.encode(newPassword));
        customer.setResetToken(null);
        customer.setResetTokenExpiry(null);
        customer.setResetTokenVerified(Boolean.FALSE);
        customerRepository.save(customer);
        return "Password reset successfully.";
    }

    private void sendOtpEmail(String email, String otp) {
        String subject = "Your OTP Code - Golden Spoon";
        String htmlContent = """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f8f9fa; padding: 24px; color: #1f2937;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e5e7eb;">
                      <div style="background: linear-gradient(135deg, #183b33, #d4b36d); padding: 24px; color: white; text-align: center;">
                        <h1 style="margin: 0; font-size: 24px;">Golden Spoon</h1>
                        <p style="margin: 8px 0 0; opacity: 0.95;">Password Reset OTP</p>
                      </div>
                      <div style="padding: 28px; text-align: center;">
                        <p style="font-size: 16px; line-height: 1.6; margin-top: 0;">Use the OTP below to verify your password reset request.</p>
                        <div style="display: inline-block; background: #f3f4f6; border: 2px dashed #d4b36d; padding: 18px 28px; border-radius: 12px; font-size: 28px; font-weight: 800; letter-spacing: 6px; color: #183b33; margin: 20px 0;">%s</div>
                        <p style="font-size: 14px; color: #6b7280; line-height: 1.6;">This OTP will expire in 5 minutes.</p>
                        <p style="font-size: 14px; color: #6b7280; line-height: 1.6; margin-bottom: 0;">If you did not request this, you can safely ignore this email.</p>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(otp);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            javaMailSender.send(message);
        } catch (MessagingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send OTP email");
        }
    }

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
