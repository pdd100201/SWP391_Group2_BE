package com.swp391.api.modules.menu.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Đại diện cho một món ăn được lưu trong bảng restaurant_menu_items.
 * Entity này chỉ mô tả cấu trúc dữ liệu; quy tắc tạo/sửa món nằm trong MenuServiceImpl.
 */
@Entity
@Table(name = "restaurant_menu_items")
public class MenuItem extends BaseAuditableEntity {
    // ID tự tăng do database sinh khi insert món mới.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tên món bắt buộc và duy nhất ở cấp database.
    @Column(nullable = false, unique = true)
    private String name;

    // Lưu thêm tên danh mục để tương thích với dữ liệu và payload cũ.
    @Column(nullable = false)
    private String category;

    // Quan hệ nhiều món thuộc một danh mục; cột khóa ngoại là menu_category_id.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_id")
    private MenuCategory menuCategory;

    // TEXT phù hợp với mô tả dài hơn giới hạn VARCHAR thông thường.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Chỉ lưu URL HTTPS của ảnh trên Cloudinary.
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    // Giá bán là số nguyên, không có phần thập phân.
    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    // true là đang phục vụ; false là tạm ngừng phục vụ nhưng bản ghi vẫn được giữ lại.
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public MenuCategory getMenuCategory() { return menuCategory; }

    // Khi gán entity danh mục, đồng thời sao chép tên danh mục sang cột category cũ.
    public void setMenuCategory(MenuCategory menuCategory) {
        this.menuCategory = menuCategory;
        if (menuCategory != null) this.category = menuCategory.getName();
    }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
