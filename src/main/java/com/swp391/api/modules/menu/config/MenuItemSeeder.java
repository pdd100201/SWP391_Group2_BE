package com.swp391.api.modules.menu.config;

import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class MenuItemSeeder implements CommandLineRunner {
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuItemSeeder(MenuItemRepository menuItemRepository, MenuCategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {
        // Không seed lặp dữ liệu. Với database cũ chỉ bổ sung giá còn thiếu rồi dừng.
        if (menuItemRepository.count() > 0) { backfillMissingPrices(); return; }
        menuItemRepository.saveAll(List.of(
                dish("Fresh Garden Salad", "Appetizer", "Fresh lettuce, tomatoes and onion with a light seasoning.", "https://res.cloudinary.com/demo/image/upload/sample.jpg", 59000),
                dish("Creamy Tomato Soup", "Appetizer", "Slow-cooked tomato soup finished with milk and butter.", "https://res.cloudinary.com/demo/image/upload/cld-sample-1.jpg", 69000),
                dish("Grilled Chicken with Jasmine Rice", "Main Course", "Grilled chicken breast served with fragrant jasmine rice.", "https://res.cloudinary.com/demo/image/upload/cld-sample-2.jpg", 149000),
                dish("Garlic Butter Salmon", "Main Course", "Pan-seared salmon with garlic butter and jasmine rice.", "https://res.cloudinary.com/demo/image/upload/cld-sample-3.jpg", 219000),
                dish("Beef Tenderloin Steak", "Main Course", "Tender beef steak with garlic butter and black pepper.", "https://res.cloudinary.com/demo/image/upload/cld-sample-4.jpg", 259000),
                dish("Garlic Butter Prawns", "Main Course", "Tiger prawns sauteed in aromatic garlic butter.", "https://res.cloudinary.com/demo/image/upload/cld-sample-5.jpg", 199000),
                dish("BBQ Pork Ribs", "Main Course", "Slow-cooked pork ribs with house seasoning.", "https://res.cloudinary.com/demo/image/upload/cld-sample.jpg", 189000)));
    }

    private void backfillMissingPrices() {
        List<MenuItem> items = menuItemRepository.findAll().stream()
                .filter(item -> item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0).toList();
        for (MenuItem item : items) { BigDecimal price = defaultPrice(item.getName()); if (price != null) item.setPrice(price); }
        if (!items.isEmpty()) menuItemRepository.saveAll(items);
    }

    private MenuItem dish(String name, String category, String description, String imageUrl, long price) {
        // Seeder vẫn phải dùng category đã tồn tại để thỏa quan hệ khóa ngoại của MenuItem.
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setMenuCategory(categoryRepository.findByNameIgnoreCase(category)
                .orElseThrow(() -> new IllegalStateException("Missing menu category: " + category)));
        item.setDescription(description); item.setImageUrl(imageUrl); item.setPrice(BigDecimal.valueOf(price)); item.setIsActive(true);
        return item;
    }

    private BigDecimal defaultPrice(String name) {
        return switch (name) {
            case "Fresh Garden Salad" -> BigDecimal.valueOf(59000); case "Creamy Tomato Soup" -> BigDecimal.valueOf(69000);
            case "Grilled Chicken with Jasmine Rice" -> BigDecimal.valueOf(149000); case "Garlic Butter Salmon" -> BigDecimal.valueOf(219000);
            case "Beef Tenderloin Steak" -> BigDecimal.valueOf(259000); case "Garlic Butter Prawns" -> BigDecimal.valueOf(199000);
            case "BBQ Pork Ribs" -> BigDecimal.valueOf(189000); default -> null;
        };
    }
}
