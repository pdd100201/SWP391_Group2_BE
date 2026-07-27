package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.NotBlank;

// Payload nhận mã khuyến mãi; @NotBlank loại null, rỗng hoặc chỉ có khoảng trắng.
public class ApplyPromotionRequest {
    @NotBlank(message = "Promotion code is required")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
