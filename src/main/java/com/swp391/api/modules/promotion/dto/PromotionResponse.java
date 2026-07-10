package com.swp391.api.modules.promotion.dto;

import com.swp391.api.modules.promotion.entity.PromotionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PromotionResponse(
        Long id,
        String code,
        String name,
        PromotionType type,
        BigDecimal value,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer usageLimit,
        Integer usedCount,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
