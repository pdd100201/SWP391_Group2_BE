package com.swp391.api.modules.account.service.impl;

import com.swp391.api.modules.account.dto.StaffRequest;
import com.swp391.api.modules.account.dto.StaffResponse;
import com.swp391.api.modules.account.dto.StatusUpdateRequest;
import com.swp391.api.modules.account.service.AccountStaffService;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getStaffList(String keyword, String role, Boolean isActive) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String r = (role == null || role.isBlank()) ? null : role.trim();
        return userRepository.findStaff(kw, r, isActive)
                .stream()
                .map(this::toStaffResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffResponse getStaffById(Long id) {
        return toStaffResponse(findStaffOrThrow(id));
    }

    @Override
    public StaffResponse createStaff(StaffRequest request) {
        if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }

        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        if (!request.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include letters and numbers");
        }

        if (userRepository.existsByUserEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getEmail());
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUserEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole().toUpperCase());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(true);

        return toStaffResponse(userRepository.save(user));
    }

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        User user = findStaffOrThrow(id);

        if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }

        if ("MANAGER".equals(getCurrentRole())
                && (isAdminOrManager(user.getRole()) || isAdminOrManager(request.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        if (!user.getUserEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByUserEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getEmail());
        }

        user.setFullName(request.getFullName());
        user.setUserEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole().toUpperCase());
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return toStaffResponse(userRepository.save(user));
    }

    @Override
    public StaffResponse updateStaffStatus(Long id, StatusUpdateRequest request) {
        User user = findStaffOrThrow(id);

        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        if (request.getIsActive() == null) {
            // Toggle: đảo ngược trạng thái hiện tại
            user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        } else {
            user.setIsActive(request.getIsActive());
        }

        return toStaffResponse(userRepository.save(user));
    }

    @Override
    public void deleteStaff(Long id) {
        User user = findStaffOrThrow(id);

        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        userRepository.delete(user);
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }

    private boolean isAdminOrManager(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }

    private User findStaffOrThrow(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Staff not found with id: " + id));
        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
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
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
