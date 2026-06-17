package com.swp391.api.modules.inventory.dto;

/**
 * DTO dùng để ghi đè thủ công hoặc reset trạng thái tồn kho của một mặt hàng.
 *
 * <p>Cho phép quản lý can thiệp vào trạng thái khi logic tự động không phản ánh
 * đúng thực tế (ví dụ: hàng đang vận chuyển, hàng đặt trước...).</p>
 *
 * <p>Cách dùng:
 * <ul>
 *   <li>Gửi {@code { "statusOverride": "IN_STOCK" }} để set thủ công</li>
 *   <li>Gửi {@code { "statusOverride": null }} để reset về tính tự động</li>
 * </ul>
 * </p>
 *
 * Set statusOverride = null to reset back to auto-calculation.
 * Valid non-null values: "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"
 */
public class UpdateStatusRequest {

    /**
     * Giá trị trạng thái muốn ghi đè.
     * <ul>
     *   <li>{@code null} - xóa ghi đè, trở về tính tự động theo số lượng</li>
     *   <li>{@code "IN_STOCK"} - đánh dấu còn hàng bất kể số lượng</li>
     *   <li>{@code "LOW_STOCK"} - đánh dấu sắp hết bất kể số lượng</li>
     *   <li>{@code "OUT_OF_STOCK"} - đánh dấu hết hàng bất kể số lượng</li>
     * </ul>
     */
    /** null resets to auto-calculation; otherwise one of IN_STOCK / LOW_STOCK / OUT_OF_STOCK */
    private String statusOverride;

    /** @return Trạng thái ghi đè */
    public String getStatusOverride() { return statusOverride; }
    /** @param statusOverride Trạng thái ghi đè cần set (hoặc null để reset) */
    public void setStatusOverride(String statusOverride) { this.statusOverride = statusOverride; }
}
