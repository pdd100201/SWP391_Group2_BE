package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Tầng truy cập dữ liệu của món ăn.
 * JpaRepository cung cấp sẵn save, findById, findAll và count mà không cần tự viết SQL.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    // Spring Data tự tạo truy vấn tìm tên món không phân biệt chữ hoa/chữ thường.
    Optional<MenuItem> findByNameIgnoreCase(String name);
}
