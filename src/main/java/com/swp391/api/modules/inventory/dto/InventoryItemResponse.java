package com.swp391.api.modules.inventory.dto;

import java.time.LocalDateTime;

public class InventoryItemResponse {

    private Long id;
    private String itemName;
    private String category;
    private String unit;
    private Double quantity;
    private Double minimumQuantity;
    private Double pricePerUnit;
    private String supplier;
    private Boolean isActive;
    private String imageUrl;
    private String status; // IN_STOCK | LOW_STOCK | OUT_OF_STOCK
    private Boolean isStatusOverridden; // true = manually set, false = auto-calculated
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InventoryItemResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getMinimumQuantity() { return minimumQuantity; }
    public void setMinimumQuantity(Double minimumQuantity) { this.minimumQuantity = minimumQuantity; }

    public Double getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(Double pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsStatusOverridden() { return isStatusOverridden; }
    public void setIsStatusOverridden(Boolean isStatusOverridden) { this.isStatusOverridden = isStatusOverridden; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
