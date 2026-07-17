package com.swp391.api.modules.promotion.repository;

import com.swp391.api.modules.promotion.entity.Promotion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByCreatedAtDesc();

    Optional<Promotion> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query(value = "SELECT COUNT(*) FROM restaurant_orders WHERE promotion_id = :promotionId", nativeQuery = true)
    long countOrdersUsingPromotion(@Param("promotionId") Long promotionId);
}
