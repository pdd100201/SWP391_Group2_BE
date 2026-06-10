package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.StaffRequest;
import com.swp391.api.modules.user.dto.StaffResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.AccountStaffService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ quản lý tài khoản nhân viên (Staff).
 * Áp dụng phân quyền: ADMIN có toàn quyền, MANAGER chỉ được thao tác
 * trên các tài khoản có role WAITER và RECEPTIONIST.
 */
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
    // LẤY DANH SÁCH NHÂN VIÊN (toàn bộ, phân trang xử lý ở frontend)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getStaffList(String keyword, User.Role role, User.Status status) {
        // Chuẩn hoá từ khoá tìm kiếm, nếu rỗng thì truyền null để truy vấn tất cả
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // Trả về toàn bộ danh sách matching, không giới hạn số lượng
        return userRepository.findStaff(kw, role, status)
                .stream()
                .map(this::toStaffResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // LẤY THÔNG TIN CHI TIẾT MỘT NHÂN VIÊN THEO ID
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public StaffResponse getStaffById(Long id) {
        User user = findStaffUserOrThrow(id);
        return toStaffResponse(user);
    }

    // -------------------------------------------------------------------------
    // TẠO TÀI KHOẢN NHÂN VIÊN MỚI
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse createStaff(StaffRequest request) {
        // Không cho phép tạo tài khoản có role CUSTOMER qua API này
        if (request.getRole() == User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }

        // MANAGER không được tạo tài khoản ADMIN hoặc MANAGER
        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        // Bắt buộc phải có mật khẩu khi tạo mới
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        // Mật khẩu phải đủ độ mạnh: tối thiểu 8 ký tự, gồm cả chữ và số
        if (!request.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include letters and numbers");
        }

        // Kiểm tra email chưa được sử dụng trong hệ thống
        if (userRepository.existsByUserEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getEmail());
        }

        // Tạo đối tượng User mới và lưu vào cơ sở dữ liệu
        User user = new User();
        user.setFullName(request.getFullName());
        user.setUserEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(User.Status.ACTIVE);

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // CẬP NHẬT THÔNG TIN NHÂN VIÊN
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        User user = findStaffUserOrThrow(id);

        // Không cho phép đổi role sang CUSTOMER
        if (request.getRole() == User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role CUSTOMER is not allowed for staff accounts");
        }

        // MANAGER không được sửa tài khoản ADMIN/MANAGER,
        // cũng không được đổi role của ai sang ADMIN/MANAGER
        if ("MANAGER".equals(getCurrentRole()) && (isAdminOrManager(user.getRole()) || isAdminOrManager(request.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        // Kiểm tra email mới chưa bị dùng bởi tài khoản khác (bỏ qua nếu là email của chính user này)
        if (!user.getUserEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByUserEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getEmail());
        }

        // Cập nhật các thông tin cơ bản
        user.setFullName(request.getFullName());
        user.setUserEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Chỉ cập nhật mật khẩu nếu client gửi lên giá trị mới (để trống = giữ nguyên)
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // CẬP NHẬT TRẠNG THÁI NHÂN VIÊN (ACTIVE / DEACTIVE)
    // -------------------------------------------------------------------------

    @Override
    public StaffResponse updateStaffStatus(Long id, StatusUpdateRequest request) {
        User user = findStaffUserOrThrow(id);

        // MANAGER không được đổi trạng thái tài khoản ADMIN hoặc MANAGER
        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        if (request.getStatus() == null) {
            // Không truyền status → tự động đảo ngược trạng thái hiện tại
            user.setStatus(user.getStatus() == User.Status.ACTIVE
                    ? User.Status.DEACTIVE : User.Status.ACTIVE);
        } else {
            // Truyền status cụ thể → gán trực tiếp
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        return toStaffResponse(saved);
    }

    // -------------------------------------------------------------------------
    // XOÁ TÀI KHOẢN NHÂN VIÊN
    // -------------------------------------------------------------------------

    @Override
    public void deleteStaff(Long id) {
        User user = findStaffUserOrThrow(id);

        // MANAGER không được xoá tài khoản ADMIN hoặc MANAGER
        if ("MANAGER".equals(getCurrentRole()) && isAdminOrManager(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Manager cannot manage ADMIN or MANAGER accounts");
        }

        userRepository.delete(user);
    }

    // -------------------------------------------------------------------------
    // CÁC PHƯƠNG THỨC HỖ TRỢ (PRIVATE HELPERS)
    // -------------------------------------------------------------------------

    /**
     * Lấy role của người dùng đang đăng nhập từ SecurityContext.
     * Trả về chuỗi rỗng nếu chưa xác thực.
     */
    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }

    /**
     * Kiểm tra role có phải ADMIN hoặc MANAGER không.
     * Dùng để xác định giới hạn thao tác của MANAGER.
     */
    private boolean isAdminOrManager(User.Role role) {
        return role == User.Role.ADMIN || role == User.Role.MANAGER;
    }

    /**
     * Tìm user theo id và đảm bảo đó là tài khoản nhân viên (không phải CUSTOMER).
     * Ném 404 nếu không tìm thấy hoặc là tài khoản khách hàng.
     */
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

    /**
     * Chuyển đổi User entity sang StaffResponse DTO để trả về cho client.
     */
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
