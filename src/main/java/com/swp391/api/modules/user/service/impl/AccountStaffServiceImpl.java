package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StaffRequest;
import com.swp391.api.modules.user.dto.StaffResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.AccountStaffService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccountStaffServiceImpl implements AccountStaffService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountStaffServiceImpl(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // -------------------------------------------------------------------------
    // GET LIST
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> getStaffList(String keyword, User.Role role,
                                                    User.Status status, int page, int size) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findStaff(kw, role, status, pageable);

        List<StaffResponse> content = userPage.getContent()
                .stream()
                .map(this::toStaffResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(content, userPage.getNumber(), userPage.getSize(),
                userPage.getTotalElements(), userPage.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public StaffResponse getStaffById(Long id) {
        User user = findStaffUserOrThrow(id);
        return toStaffResponse(user);
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse createStaff(StaffRequest request) {
        if (request.getRole() == User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (!request.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include letters and numbers");
        }
        if (userRepository.existsByUserEmail(request.getUserEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getUserEmail());
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUserEmail(request.getUserEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(User.Status.ACTIVE);

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        User user = findStaffUserOrThrow(id);

        if (request.getRole() == User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }

        // Check email uniqueness (skip if same user)
        if (!user.getUserEmail().equalsIgnoreCase(request.getUserEmail())
                && userRepository.existsByUserEmail(request.getUserEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getUserEmail());
        }

        user.setFullName(request.getFullName());
        user.setUserEmail(request.getUserEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        // Only update password when provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // STATUS TOGGLE
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse updateStaffStatus(Long id, StatusUpdateRequest request) {
        User user = findStaffUserOrThrow(id);

        if (request.getStatus() == null) {
            // Auto-toggle
            user.setStatus(user.getStatus() == User.Status.ACTIVE
                    ? User.Status.DEACTIVE : User.Status.ACTIVE);
        } else {
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Override
    public void deleteStaff(Long id) {
        User user = findStaffUserOrThrow(id);
        userRepository.delete(user);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private User findStaffUserOrThrow(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Staff not found with id: " + id));
        if (user.getRole() == User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Staff not found with id: " + id);
        }
        return user;
    }

    private StaffResponse toStaffResponse(User user) {
        return new StaffResponse(
                user.getUserId(),
                user.getFullName(),
                user.getUserEmail(),
                user.getPhone(),
                user.getRole(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
