package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tầng truy cập dữ liệu của danh mục Menu.
 * Tên method tuân theo quy ước Spring Data nên framework tự sinh câu truy vấn tương ứng.
 */
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    // Tìm danh mục theo tên và không phân biệt chữ hoa/chữ thường.
    Optional<MenuCategory> findByNameIgnoreCase(String name);

    // Lấy tất cả danh mục và sắp xếp tăng dần theo tên.
    List<MenuCategory> findAllByOrderByNameAsc();

    // Chỉ lấy danh mục đang hoạt động để đưa vào dropdown của giao diện.
    List<MenuCategory> findByIsActiveTrueOrderByNameAsc();
}
