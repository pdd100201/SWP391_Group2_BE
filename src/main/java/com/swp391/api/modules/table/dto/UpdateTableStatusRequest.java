package com.swp391.api.modules.table.dto;

import com.swp391.api.modules.table.entity.RestaurantTable;
import jakarta.validation.constraints.NotNull;

/**
 * DTO dùng để cập nhật trạng thái bàn.
 * 
 * <p>Các trạng thái hợp lệ: AVAILABLE, OCCUPIED, RESERVED, CLEANING</p>
 */
public class UpdateTableStatusRequest {

    /**
     * Trạng thái mới của bàn.
     * Phải là một trong các giá trị: AVAILABLE, OCCUPIED, RESERVED, CLEANING
     */
    @NotNull(message = "Status is required")
    private RestaurantTable.TableStatus status;

    // ============= Getters & Setters =============

    public RestaurantTable.TableStatus getStatus() {
        return status;
    }

    public void setStatus(RestaurantTable.TableStatus status) {
        this.status = status;
    }
}
