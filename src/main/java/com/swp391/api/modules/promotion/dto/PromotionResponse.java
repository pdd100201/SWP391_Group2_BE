package com.swp391.api.modules.promotion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionResponse(
        Long id,
        String code,
        String name,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer usageLimit,
        Integer usedCount,
        LocalDateTime createdAt,
}
