package com.swp391.api.modules.menu.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Đại diện cho một nhóm món như Khai vị, Món chính, Tráng miệng hoặc Đồ uống.
 * Một MenuCategory có thể được nhiều MenuItem tham chiếu qua khóa ngoại.
 */
@Entity
@Table(name = "menu_categories")
public class MenuCategory extends BaseAuditableEntity {
    // ID danh mục tự tăng, được lưu ở cột category_id.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    // Tên danh mục bắt buộc, duy nhất và dài tối đa 100 ký tự.
    @Column(name = "category_name", nullable = false, unique = true, length = 100)
    private String name;

    // Mô tả ngắn về nhóm món, tối đa 500 ký tự.
    @Column(length = 500)
    private String description;

    // Chỉ danh mục đang hoạt động mới xuất hiện trong form tạo/sửa món.
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
