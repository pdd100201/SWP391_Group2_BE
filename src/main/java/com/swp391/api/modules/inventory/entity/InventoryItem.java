package com.swp391.api.modules.inventory.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory_items")
public class InventoryItem extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "item_name", nullable = false, unique = true)
    private String itemName;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "minimum_quantity", nullable = false)
    private Double minimumQuantity;

    @Column(name = "price_per_unit")
    private Double pricePerUnit;

    @Column(name = "supplier")
    private String supplier;

    @Column(name = "image_url")
    private String imageUrl;

    /** null = auto-calculate | "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" = manual override */
    @Column(name = "status_override")
    private String statusOverride;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatusOverride() { return statusOverride; }
    public void setStatusOverride(String statusOverride) { this.statusOverride = statusOverride; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
