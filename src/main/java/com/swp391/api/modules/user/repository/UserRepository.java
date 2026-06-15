package com.swp391.api.modules.user.repository;

import com.swp391.api.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    /**
     * Lấy toàn bộ danh sách nhân viên (không phân trang), có thể lọc theo keyword/role/status.
     */
    @Query("SELECT u FROM User u WHERE u.role <> 'CUSTOMER' " +
           "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(u.userEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:role IS NULL OR u.role = :role) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "ORDER BY u.createdAt DESC")
    List<User> findStaff(@Param("keyword") String keyword,
                         @Param("role") User.Role role,
                         @Param("status") User.Status status);

    /**
     * Lấy toàn bộ danh sách khách hàng (không phân trang), có thể lọc theo keyword/status.
     */
    @Query("SELECT u FROM User u WHERE u.role = 'CUSTOMER' " +
           "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(u.userEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "ORDER BY u.createdAt DESC")
    List<User> findCustomers(@Param("keyword") String keyword,
                             @Param("status") User.Status status);
}
