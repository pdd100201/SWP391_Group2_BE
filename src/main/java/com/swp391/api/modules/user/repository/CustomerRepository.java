package com.swp391.api.modules.user.repository;

import com.swp391.api.modules.user.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomersEmail(String email);

    boolean existsByCustomersEmail(String email);

    @Query("SELECT c FROM Customer c " +
           "WHERE (:keyword IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(c.customersEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:isActive IS NULL OR c.isActive = :isActive) " +
           "ORDER BY c.createdAt DESC")
    List<Customer> findAllCustomers(@Param("keyword") String keyword,
                                    @Param("isActive") Boolean isActive);

    Optional<Customer> findByResetToken(String resetToken);

    Optional<Customer> findByOtpCode(String otpCode);
}
