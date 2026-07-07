package com.swp391.api.modules.menu.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_linked_menu_items")
// Dữ liệu gốc của món: danh mục, nguyên liệu công thức, biên lợi nhuận và trạng thái hoạt động.
public class MenuItem extends BaseAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_id")
    private MenuCategory menuCategory;

    @Column(length = 1000)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "decimal(19,2) default 0")
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "profit_margin_percent", nullable = false)
    private Double legacyPricingCompatibility = 0.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public MenuCategory getMenuCategory() { return menuCategory; }
    public void setMenuCategory(MenuCategory menuCategory) {
        this.menuCategory = menuCategory;
        if (menuCategory != null) this.category = menuCategory.getName();
    }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price == null ? BigDecimal.ZERO : price; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
