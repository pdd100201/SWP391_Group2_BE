package com.swp391.api.modules.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

// Request payload for create/update dish from the menu management screen.
public class MenuItemRequest {
    @NotBlank(message = "Dish name is required")
    private String name;

    @NotBlank(message = "Menu category is required")
    private String category;

    private Long categoryId;

    private String description;
    private String imageUrl;

    @NotNull(message = "Profit margin is required")
    @DecimalMin(value = "0.0", message = "Profit margin must be >= 0")
    private Double profitMarginPercent;

    @NotEmpty(message = "Recipe must contain at least one ingredient")
    @Valid
    private List<RecipeIngredientRequest> ingredients = new ArrayList<>();

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
    public Double getProfitMarginPercent() { return profitMarginPercent; }
    public void setProfitMarginPercent(Double profitMarginPercent) { this.profitMarginPercent = profitMarginPercent; }
    public List<RecipeIngredientRequest> getIngredients() { return ingredients; }
    public void setIngredients(List<RecipeIngredientRequest> ingredients) { this.ingredients = ingredients; }
}
