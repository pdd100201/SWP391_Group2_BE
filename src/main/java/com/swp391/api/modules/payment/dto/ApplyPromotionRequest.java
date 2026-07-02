package com.swp391.api.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class ApplyPromotionRequest {
    @NotBlank
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
