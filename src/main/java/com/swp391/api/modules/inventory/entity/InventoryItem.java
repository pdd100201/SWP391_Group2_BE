package com.swp391.api.modules.inventory.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.*;

/**
 * Entity đại diện cho một mặt hàng trong kho nguyên liệu của nhà hàng.
 * Kế thừa {@link BaseAuditableEntity} để tự động lưu thời gian tạo và cập nhật.
 *
 * <p>Bảng tương ứng trong DB: {@code inventory_items}</p>
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem extends BaseAuditableEntity {

    /**
     * Khóa chính, tự động tăng để đảm bảo mỗi mặt hàng có ID duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Tên mặt hàng - không được để trống và phải là duy nhất
     * để tránh trùng lặp dữ liệu trong kho.
     */
    @Column(name = "item_name", nullable = false, unique = true)
    private String itemName;

    /**
     * Danh mục mặt hàng (ví dụ: Vegetables, Meat, Seafood, Dairy...).
     * Giúp phân loại và lọc hàng tồn kho theo nhóm.
     */
    @Column(name = "category", nullable = false)
    private String category;

    /**
     * Đơn vị tính (ví dụ: kg, lít, chai, gram...).
     * Quan trọng để hiểu số lượng thực tế của mặt hàng.
     */
    @Column(name = "unit", nullable = false)
    private String unit;

    /**
     * Số lượng hiện có trong kho.
     * Dùng để tính toán trạng thái tồn kho tự động nếu không có override.
     */
    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Double reservedQuantity = 0.0;

    /**
     * Ngưỡng tối thiểu cần có trong kho.
     * Khi {@code quantity <= minimumQuantity}, trạng thái sẽ là LOW_STOCK.
     */
    @Column(name = "minimum_quantity", nullable = false)
    private Double minimumQuantity;

    /**
     * Giá trên mỗi đơn vị (VNĐ).
     * Dùng để tính toán chi phí tồn kho (có thể null nếu chưa có giá).
     */
    @Column(name = "price_per_unit")
    private Double pricePerUnit;

    /**
     * Tên nhà cung cấp nguyên liệu cho mặt hàng này.
     * Giúp quản lý liên hệ khi cần đặt hàng bổ sung.
     */
    @Column(name = "supplier")
    private String supplier;

    /**
     * URL ảnh minh họa cho mặt hàng, thường lấy từ Unsplash.
     * Dùng để hiển thị trên giao diện quản lý kho.
     */
    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Trạng thái được ghi đè thủ công bởi quản lý.
     * <ul>
     *   <li>{@code null} = tính tự động dựa trên số lượng</li>
     *   <li>{@code "IN_STOCK"} = còn hàng (ghi đè thủ công)</li>
     *   <li>{@code "LOW_STOCK"} = sắp hết hàng (ghi đè thủ công)</li>
     *   <li>{@code "OUT_OF_STOCK"} = hết hàng (ghi đè thủ công)</li>
     * </ul>
     */
    /** null = auto-calculate | "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" = manual override */
    @Column(name = "status_override")
    private String statusOverride;

    /**
     * Trạng thái hoạt động của mặt hàng.
     * Mặc định {@code true} - mặt hàng đang được sử dụng.
     * Khi set {@code false}, mặt hàng bị ẩn khỏi danh sách hoạt động (soft delete).
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** @return ID của mặt hàng */
    public Long getId() { return id; }
    /** @param id ID cần set */
    public void setId(Long id) { this.id = id; }

    /** @return Tên mặt hàng */
    public String getItemName() { return itemName; }
    /** @param itemName Tên mặt hàng cần set */
    public void setItemName(String itemName) { this.itemName = itemName; }

    /** @return Danh mục mặt hàng */
    public String getCategory() { return category; }
    /** @param category Danh mục cần set */
    public void setCategory(String category) { this.category = category; }

    /** @return Đơn vị tính */
    public String getUnit() { return unit; }
    /** @param unit Đơn vị tính cần set */
    public void setUnit(String unit) { this.unit = unit; }

    /** @return Số lượng hiện tại trong kho */
    public Double getQuantity() { return quantity; }
    /** @param quantity Số lượng cần set */
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getReservedQuantity() { return reservedQuantity == null ? 0.0 : reservedQuantity; }
    public void setReservedQuantity(Double reservedQuantity) {
        this.reservedQuantity = reservedQuantity == null ? 0.0 : reservedQuantity;
    }

    /** @return Ngưỡng số lượng tối thiểu */
    public Double getMinimumQuantity() { return minimumQuantity; }
    /** @param minimumQuantity Ngưỡng tối thiểu cần set */
    public void setMinimumQuantity(Double minimumQuantity) { this.minimumQuantity = minimumQuantity; }

    /** @return Giá trên mỗi đơn vị */
    public Double getPricePerUnit() { return pricePerUnit; }
    /** @param pricePerUnit Giá mỗi đơn vị cần set */
    public void setPricePerUnit(Double pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    /** @return Tên nhà cung cấp */
    public String getSupplier() { return supplier; }
    /** @param supplier Nhà cung cấp cần set */
    public void setSupplier(String supplier) { this.supplier = supplier; }

    /** @return URL ảnh minh họa */
    public String getImageUrl() { return imageUrl; }
    /** @param imageUrl URL ảnh cần set */
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    /** @return Trạng thái ghi đè thủ công (null = tính tự động) */
    public String getStatusOverride() { return statusOverride; }
    /** @param statusOverride Trạng thái ghi đè cần set */
    public void setStatusOverride(String statusOverride) { this.statusOverride = statusOverride; }

    /** @return true nếu mặt hàng đang hoạt động */
    public Boolean getIsActive() { return isActive; }
    /** @param isActive Trạng thái hoạt động cần set */
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
