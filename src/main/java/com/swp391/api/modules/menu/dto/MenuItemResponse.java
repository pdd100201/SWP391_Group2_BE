package com.swp391.api.modules.menu.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MenuItemResponse {
    private Long id;
    private String name;
    private String category;
    private String description;
    private String imageUrl;
    private Double profitMarginPercent;
    private Double foodCost;
    private Double suggestedPrice;
    private Boolean costComplete;
    private Boolean isActive;
    private String availability;
    private Integer availableServings;
    private List<String> blockingIngredients = new ArrayList<>();
    private List<RecipeIngredientResponse> ingredients = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Double getProfitMarginPercent() { return profitMarginPercent; }
    public void setProfitMarginPercent(Double profitMarginPercent) { this.profitMarginPercent = profitMarginPercent; }
    public Double getFoodCost() { return foodCost; }
    public void setFoodCost(Double foodCost) { this.foodCost = foodCost; }
    public Double getSuggestedPrice() { return suggestedPrice; }
    public void setSuggestedPrice(Double suggestedPrice) { this.suggestedPrice = suggestedPrice; }
    public Boolean getCostComplete() { return costComplete; }
    public void setCostComplete(Boolean costComplete) { this.costComplete = costComplete; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public Integer getAvailableServings() { return availableServings; }
    public void setAvailableServings(Integer availableServings) { this.availableServings = availableServings; }
    public List<String> getBlockingIngredients() { return blockingIngredients; }
    public void setBlockingIngredients(List<String> blockingIngredients) { this.blockingIngredients = blockingIngredients; }
    public List<RecipeIngredientResponse> getIngredients() { return ingredients; }
    public void setIngredients(List<RecipeIngredientResponse> ingredients) { this.ingredients = ingredients; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
