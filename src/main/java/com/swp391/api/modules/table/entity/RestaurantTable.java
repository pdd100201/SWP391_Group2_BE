package com.swp391.api.modules.table.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

/**
 * Entity đại diện cho một bàn trong nhà hàng.
 * Kế thừa {@link BaseAuditableEntity} để tự động lưu thời gian tạo và cập nhật.
 *
 * <p>Bảng tương ứng trong DB: {@code restaurant_tables}</p>
 */
@Entity
@Table(name = "restaurant_tables")
@NoArgsConstructor
public class RestaurantTable extends BaseAuditableEntity {

    /**
     * Khóa chính, tự động tăng để đảm bảo mỗi bàn có ID duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Số bàn (ví dụ: T01, T02...) - không được để trống và phải là duy nhất.
     * Dùng để xác định bàn dễ dàng trong nhà hàng.
     */
    @Column(name = "table_number", nullable = false, unique = true)
    private String tableNumber;

    /**
     * Tên bàn (ví dụ: "Cửa sổ góc", "Bàn VIP 1"...) - tùy chọn.
     * Giúp nhân viên dễ nhớ vị trí bàn.
     */
    @Column(name = "table_name")
    private String tableName;

    /**
     * Loại bàn - quan hệ @ManyToOne sang bảng {@code table_types}.
     * Cột khóa ngoại trong DB là {@code table_type_id}.
     * Dùng LAZY loading để tránh truy vấn thừa khi không cần loại bàn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_type_id", nullable = false)
    private TableType tableType;

    /**
     * Sức chứa tối đa - số ghế có thể ngồi tại bàn.
     * Bắt buộc, phải >= 1.
     */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /**
     * Trạng thái bàn: AVAILABLE, OCCUPIED, RESERVED, CLEANING.
     * AVAILABLE: Bàn trống, sẵn sàng cho khách mới
     * OCCUPIED: Có khách đang dùng bàn
     * RESERVED: Bàn được giữ cho khách đặt trước
     * CLEANING: Bàn đang được dọn dẹp
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TableStatus status = TableStatus.AVAILABLE;

    /**
     * QR Code của bàn - dùng để khách quét và xem menu, đặt hàng...
     * Lưu dưới dạng URL hoặc text encode.
     */
    @Column(name = "qr_code", columnDefinition = "LONGTEXT")
    private String qrCode;

    /**
     * Trạng thái hoạt động của bàn (active/inactive).
     * Nếu inactive, bàn không thể được sử dụng.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    public TableType getTableType() {
        return tableType;
    }

    public void setTableType(TableType tableType) {
        this.tableType = tableType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
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

    // ============= Enum for Status =============

    /**
     * Enum định nghĩa các trạng thái có thể của một bàn.
     */
    public enum TableStatus {
        AVAILABLE,   // Bàn trống
        OCCUPIED,    // Có khách
        RESERVED,    // Đã đặt
        CLEANING     // Đang dọn
    }
}
