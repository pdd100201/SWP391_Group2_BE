package com.swp391.api.modules.menu.config;

import com.swp391.api.modules.menu.entity.MenuCategory;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo các danh mục mặc định khi ứng dụng khởi động và bổ sung liên kết danh mục
 * cho những món thuộc database cũ. Đây là dữ liệu khởi tạo, không phải API người dùng gọi.
 */
@Component
@Order(3)
public class MenuCategorySeeder implements CommandLineRunner {
    // Danh sách chuẩn được tạo nếu database chưa có danh mục tương ứng.
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Appetizer", "Main Course", "Side Dish", "Dessert", "Beverage");

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;

    public MenuCategorySeeder(MenuCategoryRepository categoryRepository, MenuItemRepository menuItemRepository) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Chỉ tạo danh mục còn thiếu, vì vậy khởi động lại ứng dụng không làm trùng dữ liệu.
        for (String name : DEFAULT_CATEGORIES) {
            categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                MenuCategory category = new MenuCategory();
                category.setName(name);
                category.setDescription("Menu category: " + name);
                category.setIsActive(true);
                return categoryRepository.save(category);
            });
        }

        // Dữ liệu cũ có thể mới lưu tên category mà chưa có khóa ngoại menu_category_id.
        // Vòng lặp này gắn lại entity danh mục phù hợp cho các bản ghi đó.
        for (MenuItem item : menuItemRepository.findAll()) {
            if (item.getMenuCategory() != null) continue;
            String target = "Appetizer".equalsIgnoreCase(item.getCategory())
                    ? "Appetizer"
                    : "Main Course";

            // MenuItem đang được quản lý trong transaction nên JPA tự ghi thay đổi khi commit.
            item.setMenuCategory(categoryRepository.findByNameIgnoreCase(target).orElseThrow());
        }
    }
}
