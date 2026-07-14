package com.swp391.api.modules.menu.config;

import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import java.util.List;
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
        if (menuItemRepository.count() > 0) { backfillMissingPrices(); return; }
        menuItemRepository.saveAll(List.of(
                dish("Fresh Garden Salad", "Appetizer", "Fresh lettuce, tomatoes and onion with a light seasoning.", "https://images.unsplash.com/photo-1546793665-c74683f339c1?w=600&q=80", 59000.0),
                dish("Creamy Tomato Soup", "Appetizer", "Slow-cooked tomato soup finished with milk and butter.", "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600&q=80", 69000.0),
                dish("Grilled Chicken with Jasmine Rice", "Main Course", "Grilled chicken breast served with fragrant jasmine rice.", "https://images.unsplash.com/photo-1532550907401-a500c9a57435?w=600&q=80", 149000.0),
                dish("Garlic Butter Salmon", "Main Course", "Pan-seared salmon with garlic butter and jasmine rice.", "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=600&q=80", 219000.0),
                dish("Beef Tenderloin Steak", "Main Course", "Tender beef steak with garlic butter and black pepper.", "https://images.unsplash.com/photo-1600891964092-4316c288032e?w=600&q=80", 259000.0),
                dish("Garlic Butter Prawns", "Main Course", "Tiger prawns sauteed in aromatic garlic butter.", "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=600&q=80", 199000.0),
                dish("BBQ Pork Ribs", "Main Course", "Slow-cooked pork ribs with house seasoning.", "https://images.unsplash.com/photo-1544025162-d76694265947?w=600&q=80", 189000.0)));
    }

    private void backfillMissingPrices() {
        List<MenuItem> items = menuItemRepository.findAll().stream().filter(item -> item.getPrice() == null || item.getPrice() <= 0).toList();
        for (MenuItem item : items) { Double price = defaultPrice(item.getName()); if (price != null) item.setPrice(price); }
        if (!items.isEmpty()) menuItemRepository.saveAll(items);
    }

    private MenuItem dish(String name, String category, String description, String imageUrl, double price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setMenuCategory(categoryRepository.findByNameIgnoreCase(category)
                .orElseThrow(() -> new IllegalStateException("Missing menu category: " + category)));
        item.setDescription(description); item.setImageUrl(imageUrl); item.setPrice(price); item.setIsActive(true);
        return item;
    }

    private Double defaultPrice(String name) {
        return switch (name) {
            case "Fresh Garden Salad" -> 59000.0; case "Creamy Tomato Soup" -> 69000.0;
            case "Grilled Chicken with Jasmine Rice" -> 149000.0; case "Garlic Butter Salmon" -> 219000.0;
            case "Beef Tenderloin Steak" -> 259000.0; case "Garlic Butter Prawns" -> 199000.0;
            case "BBQ Pork Ribs" -> 189000.0; default -> null;
        };
    }
}
