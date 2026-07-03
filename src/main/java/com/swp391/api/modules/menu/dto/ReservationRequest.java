package com.swp391.api.modules.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Request to reserve a number of servings for one menu item.
public class ReservationRequest {
    @NotNull(message = "Servings is required")
    @Min(value = 1, message = "Servings must be at least 1")
    private Integer servings;

    private String referenceCode;

    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
}
