package com.swp391.api.modules.promotion.dto;

import jakarta.validation.constraints.NotNull;

public record PromotionStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean isActive
) {
}
