package com.swp391.api.modules.user.repository;

import com.swp391.api.modules.user.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomersEmail(String email);



    boolean existsByCustomersEmail(String email);

    /**
     * Find all customers joined with user for status filtering.
     */
    @Query("SELECT c FROM Customer c LEFT JOIN c.user u " +
           "WHERE (:keyword IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(c.customersEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR u.status = :status)")
    Page<Customer> findAllCustomers(@Param("keyword") String keyword,
                                    @Param("status") com.swp391.api.modules.user.entity.User.Status status,
                                    Pageable pageable);

    /**
     * Find a customer by user_id (FK).
     */
    @Query("SELECT c FROM Customer c WHERE c.user.userId = :userId")
    Optional<Customer> findByUser_UserId(@Param("userId") Long userId);
}
