package com.swp391.api.modules.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Dữ liệu gửi lên để tạo/cập nhật món từ màn quản lý thực đơn.
public class MenuItemRequest {
    @NotBlank(message = "Dish name is required")
    @Size(min = 2, max = 80, message = "Dish name must be 2-80 characters")
    private String name;

    @NotBlank(message = "Menu category is required")
    @Size(max = 80, message = "Menu category must not exceed 80 characters")
    private String category;

    private Long categoryId;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    @Pattern(regexp = "^$|https?://.+", message = "Image URL must start with http:// or https://")
    private String imageUrl;

    @NotNull(message = "Dish price is required")
    @DecimalMin(value = "1.0", message = "Dish price must be greater than 0")
    private BigDecimal price;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
