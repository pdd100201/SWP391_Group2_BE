package com.swp391.api.modules.inventory.dto;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) dùng để trả dữ liệu mặt hàng kho về phía client.
 *
 * <p>Không trả thẳng entity {@code InventoryItem} để:
 * <ul>
 *   <li>Kiểm soát chính xác những trường nào client được thấy</li>
 *   <li>Thêm trường tính toán như {@code status} và {@code isStatusOverridden}</li>
 * </ul>
 * </p>
 */
public class InventoryItemResponse {

    /** ID duy nhất của mặt hàng */
    private Long id;

    /** Tên mặt hàng */
    private String itemName;

    /** Danh mục (Vegetables, Meat, Seafood...) */
    private String category;

    /** Đơn vị tính (kg, lít, chai...) */
    private String unit;

    /** Số lượng hiện có trong kho */
    private Double quantity;

    /** Số lượng đang được giữ cho món chưa phục vụ */
    private Double reservedQuantity;

    /** Số lượng còn có thể dùng cho đơn mới */
    private Double availableQuantity;

    /** Ngưỡng số lượng tối thiểu để xác định LOW_STOCK */
    private Double minimumQuantity;

    /** Giá trên mỗi đơn vị (VNĐ) */
    private Double pricePerUnit;

    /** Tên nhà cung cấp */
    private String supplier;

    /** Trạng thái hoạt động của mặt hàng */
    private Boolean isActive;

    /** URL ảnh đại diện */
    private String imageUrl;

    /**
     * Trạng thái tồn kho cuối cùng được hiển thị cho client.
     * Có thể là: IN_STOCK | LOW_STOCK | OUT_OF_STOCK.
     * Giá trị này có thể là tự động tính hoặc ghi đè thủ công.
     */
    private String status; // IN_STOCK | LOW_STOCK | OUT_OF_STOCK

    /**
     * Cờ cho biết trạng thái hiện tại có được ghi đè thủ công hay không.
     * {@code true} = quản lý đã set thủ công; {@code false} = hệ thống tính tự động.
     * Dùng để hiển thị badge "Manual Override" trên UI.
     */
    private Boolean isStatusOverridden; // true = manually set, false = auto-calculated

    /** Thời điểm mặt hàng được tạo (tự động bởi JPA Auditing) */
    private LocalDateTime createdAt;

    /** Thời điểm mặt hàng được cập nhật lần cuối (tự động bởi JPA Auditing) */
    private LocalDateTime updatedAt;

    /**
     * Constructor rỗng cần thiết để Jackson có thể deserialize
     * và để service tạo đối tượng rồi set từng field.
     */
    public InventoryItemResponse() {}

    /** @return ID mặt hàng */
    public Long getId() { return id; }
    /** @param id ID cần set */
    public void setId(Long id) { this.id = id; }

    /** @return Tên mặt hàng */
    public String getItemName() { return itemName; }
    /** @param itemName Tên cần set */
    public void setItemName(String itemName) { this.itemName = itemName; }

    /** @return Danh mục */
    public String getCategory() { return category; }
    /** @param category Danh mục cần set */
    public void setCategory(String category) { this.category = category; }

    /** @return Đơn vị tính */
    public String getUnit() { return unit; }
    /** @param unit Đơn vị cần set */
    public void setUnit(String unit) { this.unit = unit; }

    /** @return Số lượng tồn kho */
    public Double getQuantity() { return quantity; }
    /** @param quantity Số lượng cần set */
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Double reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public Double getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Double availableQuantity) { this.availableQuantity = availableQuantity; }

    /** @return Ngưỡng tối thiểu */
    public Double getMinimumQuantity() { return minimumQuantity; }
    /** @param minimumQuantity Ngưỡng tối thiểu cần set */
    public void setMinimumQuantity(Double minimumQuantity) { this.minimumQuantity = minimumQuantity; }

    /** @return Giá mỗi đơn vị */
    public Double getPricePerUnit() { return pricePerUnit; }
    /** @param pricePerUnit Giá cần set */
    public void setPricePerUnit(Double pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    /** @return Nhà cung cấp */
    public String getSupplier() { return supplier; }
    /** @param supplier Nhà cung cấp cần set */
    public void setSupplier(String supplier) { this.supplier = supplier; }

    /** @return Trạng thái hoạt động */
    public Boolean getIsActive() { return isActive; }
    /** @param isActive Trạng thái hoạt động cần set */
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    /** @return URL ảnh */
    public String getImageUrl() { return imageUrl; }
    /** @param imageUrl URL ảnh cần set */
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    /** @return Trạng thái tồn kho (IN_STOCK/LOW_STOCK/OUT_OF_STOCK) */
    public String getStatus() { return status; }
    /** @param status Trạng thái cần set */
    public void setStatus(String status) { this.status = status; }

    /** @return true nếu trạng thái đang được ghi đè thủ công */
    public Boolean getIsStatusOverridden() { return isStatusOverridden; }
    /** @param isStatusOverridden Cờ ghi đè cần set */
    public void setIsStatusOverridden(Boolean isStatusOverridden) { this.isStatusOverridden = isStatusOverridden; }

    /** @return Thời điểm tạo */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt Thời điểm tạo cần set */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** @return Thời điểm cập nhật lần cuối */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** @param updatedAt Thời điểm cập nhật cần set */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
