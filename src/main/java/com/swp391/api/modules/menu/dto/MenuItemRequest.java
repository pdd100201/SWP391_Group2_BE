package com.swp391.api.modules.menu.dto;

import com.swp391.api.common.validation.MaxWords;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class MenuItemRequest {
    @NotBlank(message = "Dish name is required")
    @Size(max = 100, message = "Dish name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Menu category is required")
    private String category;

    private Long categoryId;

    @NotBlank(message = "Dish description is required")
    @MaxWords(value = 200, message = "Dish description must not exceed 200 words")
    private String description;

    @NotBlank(message = "Dish image is required")
    private String imageUrl;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be a positive integer")
    @Digits(integer = 12, fraction = 0, message = "Price must be a positive integer with at most 12 digits")
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
