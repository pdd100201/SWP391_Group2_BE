package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Các truy vấn lưu trữ cho quản lý menu và kiểm tra trùng tên món.
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    Optional<MenuItem> findByNameIgnoreCase(String name);
}
