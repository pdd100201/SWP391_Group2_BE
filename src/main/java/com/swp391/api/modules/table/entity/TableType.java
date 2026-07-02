package com.swp391.api.modules.table.entity;

import jakarta.persistence.*;

/**
 * Entity đại diện cho một loại bàn trong hệ thống danh mục.
 *
 * <p>Bảng tương ứng trong DB: {@code table_types}</p>
 * <p>Các bàn trong {@link RestaurantTable} tham chiếu sang bảng này qua cột {@code table_type_id}.</p>
 */
@Entity
@Table(name = "table_types")
public class TableType {

    /**
     * Khóa chính của loại bàn, tự động tăng.
     * Tương ứng với cột {@code table_type_id} trong DB.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "table_type_id")
    private Long id;

    /**
     * Tên loại bàn (ví dụ: "Main Hall", "VIP Room", "Patio"...).
     * Tương ứng với cột {@code type_name} trong DB.
     */
    @Column(name = "type_name", nullable = false, unique = true)
    private String typeName;

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 1;

    @Column(name = "status")
    private String status = "ACTIVE";

    // ============= Constructors =============

    public TableType() {
    }

    public TableType(Long id, String typeName) {
        this.id = id;
        this.typeName = typeName;
    }

    // ============= Getters & Setters =============

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
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
}
