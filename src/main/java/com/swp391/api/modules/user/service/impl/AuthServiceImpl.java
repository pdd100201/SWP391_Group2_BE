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

    // -------------------------------------------------------------------------
    // ĐĂNG KÝ KHÁCH HÀNG
    // -------------------------------------------------------------------------

    @Override
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {
        if (customerRepository.existsByCustomersEmail(request.getCustomersEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer email already exists");
        }

        Customer customer = new Customer();
        customer.setCustomersEmail(request.getCustomersEmail());
        customer.setFullName(request.getFullName());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setPhone(request.getPhone());
        customer.setAvatarUrl(request.getAvatarUrl());
        customer.setIsActive(true);
        customerRepository.save(customer);

        String token = jwtUtils.generateToken(customer.getCustomersEmail(), "CUSTOMER");
        return new AuthResponse(token, "CUSTOMER", customer.getFullName(), customer.getCustomersEmail());
    }

    // -------------------------------------------------------------------------
    // ĐĂNG NHẬP
    // -------------------------------------------------------------------------

    @Override
    public AuthResponse login(LoginRequest request) {
        // Thử staff (bảng users)
        User user = userRepository.findByUserEmail(request.getEmail()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
            }
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
            }
            String token = jwtUtils.generateToken(user.getUserEmail(), user.getRole());
            return new AuthResponse(token, user.getRole(), user.getFullName(), user.getUserEmail());
        }

        // Thử customer (bảng customers)
        Customer customer = customerRepository.findByCustomersEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email"));

        if (!passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }
        if (!Boolean.TRUE.equals(customer.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
        }

        String token = jwtUtils.generateToken(customer.getCustomersEmail(), "CUSTOMER");
        return new AuthResponse(token, "CUSTOMER", customer.getFullName(), customer.getCustomersEmail());
    }

    // -------------------------------------------------------------------------
    // ĐĂNG NHẬP GOOGLE
    // -------------------------------------------------------------------------

    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(request.getCredentialToken());
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google credential token");
            }

            Payload payload = idToken.getPayload();
            String googleEmail  = payload.getEmail();
            String googleName   = (String) payload.get("name");
            String googleAvatar = (String) payload.get("picture");

            // Thử staff
            User user = userRepository.findByUserEmail(googleEmail).orElse(null);
            if (user != null) {
                if (!Boolean.TRUE.equals(user.getIsActive())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
                }
                String token = jwtUtils.generateToken(user.getUserEmail(), user.getRole());
                return new AuthResponse(token, user.getRole(), user.getFullName(), user.getUserEmail());
            }

            // Tìm hoặc tạo mới customer
            Customer customer = customerRepository.findByCustomersEmail(googleEmail).orElse(null);
            if (customer == null) {
                customer = new Customer();
                customer.setCustomersEmail(googleEmail);
                customer.setFullName(googleName);
                customer.setAvatarUrl(googleAvatar != null ? googleAvatar : "");
                customer.setPhone(null);
                customer.setPassword(passwordEncoder.encode(generateRandomPassword()));
                customer.setIsActive(true);
                customer = customerRepository.save(customer);
            } else {
                if (!Boolean.TRUE.equals(customer.getIsActive())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been deactivated");
                }
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
