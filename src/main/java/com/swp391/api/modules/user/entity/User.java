package com.swp391.api.modules.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity đại diện cho nhân viên/người dùng nội bộ của nhà hàng (staff accounts).
 *
 * <p>Khác với {@link Customer} (khách hàng), User là tài khoản nội bộ với các vai trò như:
 * <ul>
 *   <li>{@code ADMIN} - Quản trị viên hệ thống, toàn quyền</li>
 *   <li>{@code MANAGER} - Quản lý nhà hàng, quản lý kho và báo cáo</li>
 *   <li>{@code RECEPTIONIST} - Lễ tân, quản lý đặt bàn</li>
 *   <li>{@code WAITER} - Phục vụ, xử lý order</li>
 * </ul>
 * </p>
 *
 * <p>Bảng tương ứng trong DB: {@code users}</p>
 *
 * <p>Lưu ý: Entity này không kế thừa {@link BaseAuditableEntity} vì tài khoản nhân viên
 * không cần theo dõi thời gian tạo/cập nhật trong phiên bản hiện tại.</p>
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * Khóa chính tự động tăng, định danh duy nhất cho mỗi nhân viên trong hệ thống.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    /**
     * Họ tên đầy đủ của nhân viên - bắt buộc, hiển thị trên UI và trong JWT token.
     */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    /**
     * Email đăng nhập của nhân viên - bắt buộc và duy nhất.
     * Dùng làm subject trong JWT token và là khóa tra cứu khi đăng nhập.
     */
    @Column(name = "user_email", nullable = false, unique = true)
    private String userEmail;

    /**
     * Mật khẩu đã được hash bằng BCrypt.
     * Không bao giờ lưu mật khẩu plain text - luôn hash trước khi lưu.
     */
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Số điện thoại liên lạc của nhân viên (tùy chọn).
     * Dùng để liên hệ nội bộ khi cần.
     */
    @Column(name = "phone")
    private String phone;

    /**
     * Vai trò của nhân viên trong hệ thống.
     * Quyết định quyền truy cập vào các tính năng và API endpoint.
     * Giá trị: ADMIN | MANAGER | RECEPTIONIST | WAITER
     */
    @Column(name = "role", nullable = false)
    private String role;

    /**
     * Trạng thái tài khoản nhân viên.
     * Ví dụ: "ACTIVE", "INACTIVE", "SUSPENDED".
     * Khác với isActive - đây là trạng thái có thể có nhiều giá trị hơn.
     */
    @Column(name = "status")
    private String status;

    /**
     * Cờ boolean cho biết tài khoản có đang hoạt động không.
     * {@code true} = tài khoản hợp lệ, có thể đăng nhập.
     * {@code false} = tài khoản bị vô hiệu hóa (soft delete).
     */
    @Column(name = "is_active")
    private Boolean isActive;

    /**
     * @return ID người dùng
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * @param userId ID người dùng cần set
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * @return Họ tên đầy đủ
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * @param fullName Họ tên cần set
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * @return Email đăng nhập
     */
    public String getUserEmail() {
        return userEmail;
    }

    /**
     * @param userEmail Email cần set
     */
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    /**
     * @return Mật khẩu đã hash (BCrypt)
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password Mật khẩu đã hash cần set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return Số điện thoại
     */
    public String getPhone() {
        return phone;
    }

    /**
     * @param phone Số điện thoại cần set
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * @return Vai trò (ADMIN/MANAGER/RECEPTIONIST/WAITER)
     */
    public String getRole() {
        return role;
    }

    /**
     * @param role Vai trò cần set
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * @return Trạng thái tài khoản (ACTIVE/INACTIVE...)
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status Trạng thái cần set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return true nếu tài khoản đang hoạt động
     */
    public Boolean getIsActive() {
        return isActive;
    }

    /**
     * @param isActive Trạng thái hoạt động cần set
     */
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
