package com.swp391.api.modules.user.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    public AuthServiceImpl(CustomerRepository customerRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           @Value("${jwt.secret}") String ignoredJwtSecret) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
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
        User user = userRepository.findByUserEmail(request.getEmail()).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String resetToken = jwtUtils.generateToken(user.getUserEmail(), "RESET_PASSWORD");
        // TODO: Send resetToken via email to the user
        return "Forgot password request received. Reset token generated successfully.";
    }

    @Override
    public String resetPassword(ResetPasswordRequest request) {
        // TODO: Verify reset token properly before allowing password change
        String email = request.getToken();
        User user = userRepository.findByUserEmail(email).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return "Password reset successfully.";
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
