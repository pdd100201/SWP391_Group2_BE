package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.ChangePasswordRequest;
import com.swp391.api.modules.user.dto.UpdateProfileRequest;
import com.swp391.api.modules.user.dto.UserProfileResponse;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.UserService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserProfileResponse getCurrentProfile() {
        User user = getCurrentUser();
        return mapToProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhoneNumber());
        User savedUser = userRepository.save(user);
        return mapToProfileResponse(savedUser);
    }

    @Override
    public String changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();

        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is not set");
        }

        boolean passwordMatches = passwordEncoder != null
                ? passwordEncoder.matches(request.getOldPassword(), user.getPassword())
                : user.getPassword().equals(request.getOldPassword());

        if (!passwordMatches) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        String newPassword = passwordEncoder != null
                ? passwordEncoder.encode(request.getNewPassword())
                : request.getNewPassword();

        user.setPassword(newPassword);
        userRepository.save(user);
        return "Password changed successfully";
    }

    @Override
    public UserProfileResponse updateAvatar(MultipartFile file) {
        User user = getCurrentUser();
        String mockAvatarUrl = "https://mock-storage.local/avatars/" + (file != null ? file.getOriginalFilename() : "default-avatar.png");
        user.setAvatarUrl(mockAvatarUrl);
        User savedUser = userRepository.save(user);
        return mapToProfileResponse(savedUser);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByUserEmail(email);
        return userOpt.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getUserEmail(),
                user.getUserEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl()
        );
    }
}
