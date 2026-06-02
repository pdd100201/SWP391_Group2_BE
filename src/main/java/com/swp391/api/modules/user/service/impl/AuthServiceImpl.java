package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.AuthResponse;
import com.swp391.api.modules.user.dto.CustomerRegisterRequest;
import com.swp391.api.modules.user.dto.LoginRequest;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.security.JwtUtils;
import com.swp391.api.modules.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthServiceImpl(CustomerRepository customerRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
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
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            String token = jwtUtils.generateToken(customer.getCustomersEmail(), "CUSTOMER");
            return new AuthResponse(token, "CUSTOMER", customer.getFullName(), customer.getCustomersEmail());
        }

        User user = userRepository.findByUserEmail(request.getEmail()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            String role = user.getRole();
            String token = jwtUtils.generateToken(user.getUserEmail(), role);
            return new AuthResponse(token, role, user.getFullName(), user.getUserEmail());
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
