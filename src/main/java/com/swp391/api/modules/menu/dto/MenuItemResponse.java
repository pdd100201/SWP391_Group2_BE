package com.swp391.api.modules.menu.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dữ liệu món ăn được backend trả về cho frontend.
 * DTO này tách giao diện API khỏi entity database và bổ sung trạng thái availability dễ hiển thị.
 */
public class MenuItemResponse {
    // Thông tin nhận diện và nội dung chính của món.
    private Long id;
    private String name;
    private String category;
    private Long categoryId;
    private String description;
    private String imageUrl;
    private BigDecimal price;

    // isActive là trạng thái lưu trong database; availability là nhãn tính toán cho giao diện.
    private Boolean isActive;
    private String availability;

    // Thời điểm tạo và cập nhật được JPA Auditing tự động ghi nhận.
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getter/setter cho phép Spring chuyển đối tượng này thành JSON response.
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
