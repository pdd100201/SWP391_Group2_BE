package com.swp391.api.modules.inventory.dto;

/**
 * DTO for manually overriding or resetting inventory item status.
 * Set statusOverride = null to reset back to auto-calculation.
 * Valid non-null values: "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"
 */
public class UpdateStatusRequest {

    /** null resets to auto-calculation; otherwise one of IN_STOCK / LOW_STOCK / OUT_OF_STOCK */
    private String statusOverride;

    public String getStatusOverride() { return statusOverride; }
    public void setStatusOverride(String statusOverride) { this.statusOverride = statusOverride; }
}
