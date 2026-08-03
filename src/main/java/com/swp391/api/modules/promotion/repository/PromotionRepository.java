package com.swp391.api.modules.promotion.repository;

import com.swp391.api.modules.promotion.entity.Promotion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT p
            FROM Promotion p
            WHERE (:keyword IS NULL
                   OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:active IS NULL OR p.isActive = :active)
            """)
    Page<Promotion> searchPromotions(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable
    );

    Optional<Promotion> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query(value = "SELECT COUNT(*) FROM bills WHERE promotion_id = :promotionId", nativeQuery = true)
    long countBillsUsingPromotion(@Param("promotionId") Long promotionId);
}
