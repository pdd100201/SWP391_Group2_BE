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
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserProfileResponse getCurrentProfile() {
        // Lấy tài khoản hiện tại đang đăng nhập rồi map sang response phù hợp.
        Object account = getCurrentAccount();
        if (account instanceof User user) {
            return mapUserToProfileResponse(user);
        }
        Customer customer = (Customer) account;
        return mapCustomerToProfileResponse(customer);
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        // Cập nhật thông tin cá nhân cho đúng loại tài khoản đang đăng nhập.
        Object account = getCurrentAccount();
        if (account instanceof User user) {
            user.setFullName(request.getFullName());
            user.setPhone(request.getPhoneNumber());
            return mapUserToProfileResponse(userRepository.save(user));
        }
        Customer customer = (Customer) account;
        customer.setFullName(request.getFullName());
        customer.setPhone(request.getPhoneNumber());
        Customer saved = customerRepository.save(customer);
        return mapCustomerToProfileResponse(saved);
    }

    @Override
    public String changePassword(ChangePasswordRequest request) {
        // Đổi mật khẩu cho user hoặc customer hiện tại.
        Object account = getCurrentAccount();
        if (account instanceof User user) {
            return changePasswordForUser(user, request);
        }
        Customer customer = (Customer) account;
        return changePasswordForCustomer(customer, request);
    }

    @Override
    public UserProfileResponse updateAvatar(MultipartFile file) {
        // Tạo đường dẫn avatar tạm và lưu lại cho tài khoản hiện tại.
        Object account = getCurrentAccount();
        String avatarUrl = "https://mock-storage.local/avatars/" + (file != null ? file.getOriginalFilename() : "default-avatar.png");

        if (account instanceof User user) {
            user.setAvatarUrl(avatarUrl);
            return mapUserToProfileResponse(userRepository.save(user));
        }

        Customer customer = (Customer) account;
        customer.setAvatarUrl(avatarUrl);
        return mapCustomerToProfileResponse(customerRepository.save(customer));
    }

    private Object getCurrentAccount() {
        // Lấy email từ SecurityContext để xác định user đang đăng nhập.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByUserEmail(email);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        return customerRepository.findByCustomersEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private String changePasswordForUser(User user, ChangePasswordRequest request) {
        // Kiểm tra mật khẩu cũ và mã hóa mật khẩu mới trước khi lưu.
        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is not set");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return "Password changed successfully";
    }

    private String changePasswordForCustomer(Customer customer, ChangePasswordRequest request) {
        // Kiểm tra mật khẩu cũ và mã hóa mật khẩu mới cho customer.
        if (customer.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is not set");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), customer.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        customerRepository.save(customer);
        return "Password changed successfully";
    }

    private UserProfileResponse mapUserToProfileResponse(User user) {
        // Chuyển entity User sang response trả về cho FE.
        return new UserProfileResponse(
                user.getUserId(),
                user.getUserEmail(),
                user.getUserEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl()
        );
    }

    private UserProfileResponse mapCustomerToProfileResponse(Customer customer) {
        // Chuyển entity Customer sang response trả về cho FE.
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
