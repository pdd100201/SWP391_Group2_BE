package com.swp391.api.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO dùng để chỉnh sửa các thông tin vận hành của một mặt hàng kho.
 * Tên mặt hàng và ảnh không nằm trong request này để tránh thay đổi ngoài ý muốn.
 */
public class UpdateInventoryItemRequest {

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0", message = "Quantity must be >= 0")
    private Double quantity;

    @NotNull(message = "Minimum quantity is required")
    @DecimalMin(value = "0.0", message = "Minimum quantity must be >= 0")
    private Double minimumQuantity;

    @DecimalMin(value = "0.0", message = "Price per unit must be >= 0")
    private Double pricePerUnit;

    private String supplier;
    private String imageUrl;

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

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
