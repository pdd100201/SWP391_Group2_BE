package com.swp391.api.modules.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

// One recipe row selected in the menu form: inventory item plus quantity required per serving.
public class RecipeIngredientRequest {
    @NotNull(message = "Inventory item is required")
    private Long inventoryItemId;

    @NotNull(message = "Required quantity is required")
    @DecimalMin(value = "0.0001", message = "Required quantity must be greater than 0")
    private Double requiredQuantity;

    public Long getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(Long inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public Double getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(Double requiredQuantity) { this.requiredQuantity = requiredQuantity; }
}
