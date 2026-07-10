package com.swp391.api.modules.promotion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionResponse(
        Long id,
        String code,
        String name,
        String description,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer usageLimit,
        Integer usedCount,
        String status,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
