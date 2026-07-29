package com.swp391.api.modules.menu.controller;

import com.swp391.api.modules.menu.dto.MenuCategoryResponse;
import com.swp391.api.modules.menu.entity.MenuCategory;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cung cấp danh sách danh mục đang hoạt động cho ô chọn danh mục trên giao diện Menu.
 * Module hiện chỉ cho đọc danh mục, chưa có API tạo/sửa/xóa danh mục từ giao diện.
 */
@RestController
@RequestMapping("/api/menu-categories")
public class MenuCategoryController {
    // Repository được dùng trực tiếp vì endpoint này chỉ đọc và chuyển đổi dữ liệu đơn giản.
    private final MenuCategoryRepository categoryRepository;

    public MenuCategoryController(MenuCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<MenuCategoryResponse>> getActiveCategories() {
        // Chỉ trả danh mục đang hoạt động và sắp xếp theo tên để dropdown luôn hiển thị ổn định.
        return ResponseEntity.ok(categoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList());
    }

    private MenuCategoryResponse toResponse(MenuCategory category) {
        // Không trả thẳng entity ra ngoài API; chỉ lấy bốn trường giao diện thực sự cần.
        return new MenuCategoryResponse(
                category.getId(), category.getName(), category.getDescription(), category.getIsActive());
    }
}
