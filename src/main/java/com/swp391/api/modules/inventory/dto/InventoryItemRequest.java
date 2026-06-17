package com.swp391.api.modules.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO (Data Transfer Object) dùng để nhận dữ liệu từ client khi tạo mới một mặt hàng kho.
 *
 * <p>Các trường bắt buộc được validate bằng annotation Bean Validation.
 * Nếu vi phạm, {@code GlobalExceptionHandler} sẽ trả về lỗi 400 với chi tiết từng field.</p>
 */
public class InventoryItemRequest {

    /**
     * Tên mặt hàng - bắt buộc, không được rỗng.
     * Dùng để xác định mặt hàng và kiểm tra trùng lặp trong DB.
     */
    @NotBlank(message = "Item name is required")
    private String itemName;

    /**
     * Danh mục mặt hàng (Vegetables, Meat, Seafood...) - bắt buộc.
     * Dùng để phân loại và lọc hàng tồn kho.
     */
    @NotBlank(message = "Category is required")
    private String category;

    /**
     * Đơn vị tính (kg, lít, chai...) - bắt buộc.
     * Cần thiết để hiểu ngữ nghĩa của trường quantity.
     */
    @NotBlank(message = "Unit is required")
    private String unit;

    /**
     * Số lượng ban đầu trong kho - bắt buộc, phải >= 0.
     * Không cho phép số âm vì không thể có số lượng tồn kho âm.
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be >= 0")
    private Double quantity;

    /**
     * Ngưỡng tối thiểu để xác định LOW_STOCK - bắt buộc, phải >= 0.
     * Khi quantity <= minimumQuantity thì trạng thái là LOW_STOCK.
     */
    @NotNull(message = "Minimum quantity is required")
    @Min(value = 0, message = "Minimum quantity must be >= 0")
    private Double minimumQuantity;

    /**
     * Giá trên mỗi đơn vị (VNĐ) - không bắt buộc.
     * Có thể null nếu chưa biết giá tại thời điểm nhập.
     */
    private Double pricePerUnit;

    /**
     * Tên nhà cung cấp - không bắt buộc.
     * Giúp tra cứu nguồn nhập hàng khi cần tái nhập.
     */
    private String supplier;

    /**
     * URL ảnh đại diện cho mặt hàng - không bắt buộc.
     * Thường là link ảnh từ Unsplash hoặc CDN nội bộ.
     */
    private String imageUrl;

    /** @return Tên mặt hàng */
    public String getItemName() { return itemName; }
    /** @param itemName Tên mặt hàng cần set */
    public void setItemName(String itemName) { this.itemName = itemName; }

    /** @return Danh mục */
    public String getCategory() { return category; }
    /** @param category Danh mục cần set */
    public void setCategory(String category) { this.category = category; }

    /** @return Đơn vị tính */
    public String getUnit() { return unit; }
    /** @param unit Đơn vị tính cần set */
    public void setUnit(String unit) { this.unit = unit; }

    /** @return Số lượng */
    public Double getQuantity() { return quantity; }
    /** @param quantity Số lượng cần set */
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    /** @return Ngưỡng tối thiểu */
    public Double getMinimumQuantity() { return minimumQuantity; }
    /** @param minimumQuantity Ngưỡng tối thiểu cần set */
    public void setMinimumQuantity(Double minimumQuantity) { this.minimumQuantity = minimumQuantity; }

    /** @return Giá mỗi đơn vị */
    public Double getPricePerUnit() { return pricePerUnit; }
    /** @param pricePerUnit Giá mỗi đơn vị cần set */
    public void setPricePerUnit(Double pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    /** @return Nhà cung cấp */
    public String getSupplier() { return supplier; }
    /** @param supplier Nhà cung cấp cần set */
    public void setSupplier(String supplier) { this.supplier = supplier; }

    /** @return URL ảnh */
    public String getImageUrl() { return imageUrl; }
    /** @param imageUrl URL ảnh cần set */
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
