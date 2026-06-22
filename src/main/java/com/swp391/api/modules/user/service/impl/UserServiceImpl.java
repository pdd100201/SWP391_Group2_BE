package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.ChangePasswordRequest;
import com.swp391.api.modules.user.dto.CustomerProfileResponse;
import com.swp391.api.modules.user.dto.UpdateProfileRequest;
import com.swp391.api.modules.user.dto.UserProfileResponse;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.UserService;
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
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserProfileResponse getCurrentProfile() {
        // Lấy tài khoản hiện tại đang đăng nhập rồi map sang response phù hợp.
        CurrentAccount account = getCurrentAccount();
        return account.toProfileResponse();
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        // Cập nhật thông tin cá nhân cho đúng loại tài khoản đang đăng nhập.
        CurrentAccount account = getCurrentAccount();
        account.setFullName(request.getFullName());
        account.setPhone(request.getPhoneNumber());
        return saveAndMap(account);
    }

    @Override
    public String changePassword(ChangePasswordRequest request) {
        // Đổi mật khẩu cho user hoặc customer hiện tại.
        CurrentAccount account = getCurrentAccount();
        String currentPassword = account.getPassword();

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is not set");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        save(account);
        return "Password changed successfully";
    }

    @Override
    public UserProfileResponse updateAvatar(MultipartFile file) {
        // Tạo đường dẫn avatar tạm và lưu lại cho tài khoản hiện tại.
        CurrentAccount account = getCurrentAccount();
        String mockAvatarUrl = "https://mock-storage.local/avatars/" + (file != null ? file.getOriginalFilename() : "default-avatar.png");
        account.setAvatarUrl(mockAvatarUrl);
        return saveAndMap(account);
    }

    private CurrentAccount getCurrentAccount() {
        // Lấy email từ SecurityContext để xác định user đang đăng nhập.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String email = authentication.getName();

        User user = userRepository.findByUserEmail(email).orElse(null);
        if (user != null) {
            return CurrentAccount.fromUser(user);
        }

        Customer customer = customerRepository.findByCustomersEmail(email).orElse(null);
        if (customer != null) {
            return CurrentAccount.fromCustomer(customer);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    private UserProfileResponse saveAndMap(CurrentAccount account) {
        return save(account).toProfileResponse();
    }

    private CurrentAccount save(CurrentAccount account) {
        if (account.user != null) {
            account.user = userRepository.save(account.user);
            return account;
        }
        account.customer = customerRepository.save(account.customer);
        return account;
    }

    private static class CurrentAccount {
        private User user;
        private Customer customer;

        private static CurrentAccount fromUser(User user) {
            CurrentAccount account = new CurrentAccount();
            account.user = user;
            return account;
        }

        private static CurrentAccount fromCustomer(Customer customer) {
            CurrentAccount account = new CurrentAccount();
            account.customer = customer;
            return account;
        }

        private String getPassword() {
            return user != null ? user.getPassword() : customer.getPassword();
        }

        private void setPassword(String password) {
            if (user != null) {
                user.setPassword(password);
            } else {
                customer.setPassword(password);
            }
        }

        private void setFullName(String fullName) {
            if (user != null) {
                user.setFullName(fullName);
            } else {
                customer.setFullName(fullName);
            }
        }

        private void setPhone(String phone) {
            if (user != null) {
                user.setPhone(phone);
            } else {
                customer.setPhone(phone);
            }
        }

        private void setAvatarUrl(String avatarUrl) {
            if (user != null) {
                user.setAvatarUrl(avatarUrl);
            } else {
                customer.setAvatarUrl(avatarUrl);
            }
        }

        private UserProfileResponse toProfileResponse() {
            if (user != null) {
                return new UserProfileResponse(
                        user.getUserId(),
                        user.getUserEmail(),
                        user.getUserEmail(),
                        user.getFullName(),
                        user.getPhone(),
                        user.getAvatarUrl()
                );
            }
            return new UserProfileResponse(
                    customer.getCustomerId(),
                    customer.getCustomersEmail(),
                    customer.getCustomersEmail(),
                    customer.getFullName(),
                    customer.getPhone(),
                    customer.getAvatarUrl()
            );
        }
    }
}
