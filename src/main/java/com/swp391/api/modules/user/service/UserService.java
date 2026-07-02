package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.ChangePasswordRequest;
import com.swp391.api.modules.user.dto.UpdateProfileRequest;
import com.swp391.api.modules.user.dto.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserProfileResponse getCurrentProfile();
    UserProfileResponse updateProfile(UpdateProfileRequest request);
    String changePassword(ChangePasswordRequest request);
    UserProfileResponse updateAvatar(MultipartFile file);
}
