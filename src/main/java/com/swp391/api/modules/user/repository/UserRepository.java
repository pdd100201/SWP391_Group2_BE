package com.swp391.api.modules.user.repository;

import com.swp391.api.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    /**
     * Find staff accounts (role != CUSTOMER) with optional search and filters.
     */
    @Query("SELECT u FROM User u WHERE u.role <> 'CUSTOMER' " +
           "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(u.userEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:role IS NULL OR u.role = :role) " +
           "AND (:status IS NULL OR u.status = :status)")
    Page<User> findStaff(@Param("keyword") String keyword,
                         @Param("role") User.Role role,
                         @Param("status") User.Status status,
                         Pageable pageable);

    /**
     * Find customer accounts (role = CUSTOMER) with optional search and filters.
     */
    @Query("SELECT u FROM User u WHERE u.role = 'CUSTOMER' " +
           "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(u.userEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR u.status = :status)")
    Page<User> findCustomers(@Param("keyword") String keyword,
                             @Param("status") User.Status status,
                             Pageable pageable);
}
