package com.swp391.api.modules.promotion.dto;

import com.swp391.api.modules.promotion.entity.DiscountType;
import com.swp391.api.modules.promotion.entity.PromotionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionRequest(
        @NotBlank(message = "Promotion code is required")
        String code,

        @NotBlank(message = "Promotion name is required")
        String name,

        String description,

        @NotNull(message = "Discount type is required")
        DiscountType type,

        @NotNull(message = "Discount value is required")
        BigDecimal value,

        BigDecimal minOrderAmount,

        BigDecimal maxDiscountAmount,

        @NotNull(message = "Start date is required")
        LocalDateTime startDate,

        @NotNull(message = "End date is required")
        LocalDateTime endDate,

        Integer usageLimit,

        PromotionStatus status
) {
}
