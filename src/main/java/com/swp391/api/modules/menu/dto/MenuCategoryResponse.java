package com.swp391.api.modules.menu.dto;

/**
 * Bản dữ liệu danh mục rút gọn dùng cho dropdown chọn danh mục trên giao diện.
 * Record tự tạo constructor và các hàm đọc dữ liệu nên không cần viết getter thủ công.
 */
public record MenuCategoryResponse(Long id, String name, String description, Boolean isActive) {
}
