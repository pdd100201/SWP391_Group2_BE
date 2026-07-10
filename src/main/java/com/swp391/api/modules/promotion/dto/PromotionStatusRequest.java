package com.swp391.api.modules.promotion.dto;

import jakarta.validation.constraints.NotNull;

public class PromotionStatusRequest {
    @NotNull
    private Boolean isActive;

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
