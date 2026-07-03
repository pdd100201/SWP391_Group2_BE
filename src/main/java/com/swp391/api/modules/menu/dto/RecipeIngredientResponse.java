package com.swp391.api.modules.menu.dto;

// Recipe row returned to FE, including inventory stock and computed ingredient cost.
public class RecipeIngredientResponse {
    private Long inventoryItemId;
    private String inventoryItemName;
    private String unit;
    private Double requiredQuantity;
    private Double inventoryQuantity;
    private Double reservedQuantity;
    private Double availableQuantity;
    private Double pricePerUnit;
    private Double ingredientCost;
    private Boolean inventoryActive;

    public Long getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(Long inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public String getInventoryItemName() { return inventoryItemName; }
    public void setInventoryItemName(String inventoryItemName) { this.inventoryItemName = inventoryItemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(Double requiredQuantity) { this.requiredQuantity = requiredQuantity; }
    public Double getInventoryQuantity() { return inventoryQuantity; }
    public void setInventoryQuantity(Double inventoryQuantity) { this.inventoryQuantity = inventoryQuantity; }
    public Double getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Double reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Double getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Double availableQuantity) { this.availableQuantity = availableQuantity; }
    public Double getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(Double pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public Double getIngredientCost() { return ingredientCost; }
    public void setIngredientCost(Double ingredientCost) { this.ingredientCost = ingredientCost; }
    public Boolean getInventoryActive() { return inventoryActive; }
    public void setInventoryActive(Boolean inventoryActive) { this.inventoryActive = inventoryActive; }
}
