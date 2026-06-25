package com.swp391.api.modules.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO (Data Transfer Object) dùng để nhận dữ liệu từ client khi tạo/cập nhật bàn.
 *
 * <p>Các trường bắt buộc được validate bằng annotation Bean Validation.
 * Nếu vi phạm, {@code GlobalExceptionHandler} sẽ trả về lỗi 400 với chi tiết từng field.</p>
 */
public class TableRequest {

    /**
     * Số bàn (ví dụ: T01, T02...) - bắt buộc, không được rỗng.
     * Phải là duy nhất trong hệ thống.
     */
    @NotBlank(message = "Table number is required")
    private String tableNumber;

    /**
     * Tên bàn (ví dụ: "Cửa sổ góc"...) - tùy chọn nhưng nên có.
     */
    private String tableName;

    /**
     * ID loại bàn - khóa ngoại tham chiếu sang bảng {@code table_types}.
     * Client gửi lên giá trị số (ví dụ: 1 = Main Hall, 2 = VIP Room).
     * Bắt buộc, không được null.
     */
    private Long tableTypeId;

    /**
     * Ten loai ban dang chu. Giu lai de frontend cu van co the gui "Main Hall",
     * "VIP Room"... trong khi database moi luu khoa ngoai table_type_id.
     */
    private String tableType;

    /**
     * Sức chứa - số ghế có thể ngồi tại bàn. Bắt buộc, phải >= 1.
     */
    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be >= 1")
    private Integer capacity;

    /**
     * QR Code của bàn - tùy chọn.
     */
    private String qrCode;

    // ============= Getters & Setters =============

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Long getTableTypeId() {
        return tableTypeId;
    }

    public void setTableTypeId(Long tableTypeId) {
        this.tableTypeId = tableTypeId;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }
}
