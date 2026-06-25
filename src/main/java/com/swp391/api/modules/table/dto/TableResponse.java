package com.swp391.api.modules.table.dto;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) dùng để trả dữ liệu bàn về phía client.
 *
 * <p>Không trả thẳng entity {@code RestaurantTable} để:
 * <ul>
 *   <li>Kiểm soát chính xác những trường nào client được thấy</li>
 *   <li>Có thể thêm/bỏ trường tính toán mà không cần thay đổi entity</li>
 * </ul>
 * </p>
 */
public class TableResponse {

    /** ID duy nhất của bàn */
    private Long id;

    /** Số bàn */
    private String tableNumber;

    /** Tên bàn */
    private String tableName;

    /** ID loại bàn (dùng cho form edit / dropdown) */
    private Long tableTypeId;

    /** Tên loại bàn (dùng để hiển thị trực tiếp, lấy từ type_name của bảng table_types) */
    private String tableType;

    /** Sức chứa */
    private Integer capacity;

    /** Trạng thái bàn */
    private String status;

    /** QR Code */
    private String qrCode;

    /** Trạng thái hoạt động */
    private Boolean isActive;

    /** Thời gian tạo */
    private LocalDateTime createdAt;

    /** Thời gian cập nhật cuối cùng */
    private LocalDateTime updatedAt;

    // ============= Constructors =============

    public TableResponse() {
    }

    public TableResponse(Long id, String tableNumber, String tableName,
                         Long tableTypeId, String tableType,
                         Integer capacity, String status, String qrCode, Boolean isActive,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tableNumber = tableNumber;
        this.tableName = tableName;
        this.tableTypeId = tableTypeId;
        this.tableType = tableType;
        this.capacity = capacity;
        this.status = status;
        this.qrCode = qrCode;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ============= Getters & Setters =============

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
