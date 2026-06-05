package com.swp391.api.modules.user.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.swp391.api.modules.user.dto.AuthResponse;
import com.swp391.api.modules.user.dto.CustomerRegisterRequest;
import com.swp391.api.modules.user.dto.GoogleLoginRequest;
import com.swp391.api.modules.user.dto.LoginRequest;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.common.security.JwtUtils;
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
        // Check duplicate in both tables
        customerRepository.findByCustomersEmail(request.getCustomersEmail())
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer email already exists");
                });
        userRepository.findByUserEmail(request.getCustomersEmail())
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer email already exists");
                });

        // Create User row (holds password & role)
        User user = new User();
        user.setFullName(request.getFullName());
        user.setUserEmail(request.getCustomersEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(User.Role.CUSTOMER);
        user.setStatus(User.Status.ACTIVE);
        user.setAvatarUrl(request.getAvatarUrl());
        User savedUser = userRepository.save(user);

        // Create Customer profile row (no password)
        Customer customer = new Customer();
        customer.setUser(savedUser);
        customer.setFullName(request.getFullName());
        customer.setCustomersEmail(request.getCustomersEmail());
        customer.setPhone(request.getPhone());
        customer.setAvatarUrl(request.getAvatarUrl());
        customerRepository.save(customer);

        String token = jwtUtils.generateToken(savedUser.getUserEmail(), "CUSTOMER");
        return new AuthResponse(token, "CUSTOMER", savedUser.getFullName(), savedUser.getUserEmail());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // All accounts (including CUSTOMER) authenticate via the users table
        User user = userRepository.findByUserEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }

        String role = user.getRole().name();
        String token = jwtUtils.generateToken(user.getUserEmail(), role);
        return new AuthResponse(token, role, user.getFullName(), user.getUserEmail());
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
                String token = jwtUtils.generateToken(user.getUserEmail(), user.getRole().name());
                return new AuthResponse(token, user.getRole().name(), user.getFullName(), user.getUserEmail());
            }

            Customer customer = customerRepository.findByCustomersEmail(googleEmail).orElse(null);
            if (customer == null) {
                // Create User row first (password stored here, not on Customer)
                User newUser = new User();
                newUser.setUserEmail(googleEmail);
                newUser.setFullName(googleName);
                newUser.setAvatarUrl(googleAvatar != null ? googleAvatar : "");
                newUser.setPhone(null);
                newUser.setRole(User.Role.CUSTOMER);
                newUser.setStatus(User.Status.ACTIVE);
                newUser.setPassword(passwordEncoder.encode(generateRandomPassword()));
                User savedUser = userRepository.save(newUser);

                // Create linked Customer profile (no password)
                Customer newCustomer = new Customer();
                newCustomer.setUser(savedUser);
                newCustomer.setCustomersEmail(googleEmail);
                newCustomer.setFullName(googleName);
                newCustomer.setAvatarUrl(googleAvatar != null ? googleAvatar : "");
                newCustomer.setPhone(null);
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

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
