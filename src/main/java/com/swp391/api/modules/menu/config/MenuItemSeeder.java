package com.swp391.api.modules.menu.config;

import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Order(4)
public class MenuItemSeeder implements CommandLineRunner {
    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "Fresh Garden Salad", BigDecimal.valueOf(14000),
            "Creamy Tomato Soup", BigDecimal.valueOf(27000),
            "Grilled Chicken with Jasmine Rice", BigDecimal.valueOf(54000),
            "Garlic Butter Salmon", BigDecimal.valueOf(154000),
            "Beef Tenderloin Steak", BigDecimal.valueOf(190000),
            "Garlic Butter Prawns", BigDecimal.valueOf(184000),
            "BBQ Pork Ribs", BigDecimal.valueOf(85000)
    );

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuItemSeeder(MenuItemRepository menuItemRepository, MenuCategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {
        if (menuItemRepository.count() > 0) {
            backfillSeedPrices();
            return;
        }

        menuItemRepository.save(dish(
                "Fresh Garden Salad",
                "Appetizer",
                "Fresh lettuce, tomatoes and onion with a light seasoning.",
                "https://images.unsplash.com/photo-1546793665-c74683f339c1?w=600&q=80",
                DEFAULT_PRICES.get("Fresh Garden Salad")
        ));
        menuItemRepository.save(dish(
                "Creamy Tomato Soup",
                "Appetizer",
                "Slow-cooked tomato soup finished with milk and butter.",
                "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600&q=80",
                DEFAULT_PRICES.get("Creamy Tomato Soup")
        ));
        menuItemRepository.save(dish(
                "Grilled Chicken with Jasmine Rice",
                "Main Course",
                "Grilled chicken breast served with fragrant jasmine rice.",
                "https://images.unsplash.com/photo-1532550907401-a500c9a57435?w=600&q=80",
                DEFAULT_PRICES.get("Grilled Chicken with Jasmine Rice")
        ));
        menuItemRepository.save(dish(
                "Garlic Butter Salmon",
                "Main Course",
                "Pan-seared salmon with garlic butter and jasmine rice.",
                "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=600&q=80",
                DEFAULT_PRICES.get("Garlic Butter Salmon")
        ));
        menuItemRepository.save(dish(
                "Beef Tenderloin Steak",
                "Main Course",
                "Tender beef steak with garlic butter and black pepper.",
                "https://images.unsplash.com/photo-1600891964092-4316c288032e?w=600&q=80",
                DEFAULT_PRICES.get("Beef Tenderloin Steak")
        ));
        menuItemRepository.save(dish(
                "Garlic Butter Prawns",
                "Main Course",
                "Tiger prawns sauteed in aromatic garlic butter.",
                "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=600&q=80",
                DEFAULT_PRICES.get("Garlic Butter Prawns")
        ));
        menuItemRepository.save(dish(
                "BBQ Pork Ribs",
                "Main Course",
                "Slow-cooked pork ribs with house seasoning.",
                "https://images.unsplash.com/photo-1544025162-d76694265947?w=600&q=80",
                DEFAULT_PRICES.get("BBQ Pork Ribs")
        ));
        System.out.println("[MenuSeeder] Seeded " + DEFAULT_PRICES.size() + " menu items.");
    }

    private void backfillSeedPrices() {
        menuItemRepository.findAll().forEach(item -> {
            BigDecimal defaultPrice = DEFAULT_PRICES.get(item.getName());
            if (defaultPrice != null && (item.getPrice() == null || BigDecimal.ZERO.compareTo(item.getPrice()) == 0)) {
                item.setPrice(defaultPrice);
                menuItemRepository.save(item);
            }
        });
    }

    private MenuItem dish(
            String name,
            String category,
            String description,
            String imageUrl,
            BigDecimal price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setCategory(category);
        item.setMenuCategory(categoryRepository.findByNameIgnoreCase(category)
                .orElseThrow(() -> new IllegalStateException("Missing menu category: " + category)));
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setPrice(price);
        item.setIsActive(true);
        return item;
    }
}
