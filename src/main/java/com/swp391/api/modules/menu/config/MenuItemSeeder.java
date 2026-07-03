package com.swp391.api.modules.menu.config;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.entity.RecipeIngredient;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(4)
// Bootstraps sample dishes after categories and inventory have been seeded.
public class MenuItemSeeder implements CommandLineRunner {
    private final MenuItemRepository menuItemRepository;
    private final InventoryRepository inventoryRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuItemSeeder(
            MenuItemRepository menuItemRepository,
            InventoryRepository inventoryRepository,
            MenuCategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {
        if (menuItemRepository.count() > 0 || inventoryRepository.count() == 0) return;

        List<MenuItem> items = List.of(
                dish(
                        "Fresh Garden Salad",
                        "Appetizer",
                        "Fresh lettuce, tomatoes and onion with a light seasoning.",
                        "https://images.unsplash.com/photo-1546793665-c74683f339c1?w=600&q=80",
                        120.0,
                        ingredient("Lettuce", 0.15),
                        ingredient("Fresh Tomatoes", 0.10),
                        ingredient("White Onion", 0.03),
                        ingredient("Black Pepper", 1.0)
                ),
                dish(
                        "Creamy Tomato Soup",
                        "Appetizer",
                        "Slow-cooked tomato soup finished with milk and butter.",
                        "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600&q=80",
                        130.0,
                        ingredient("Fresh Tomatoes", 0.25),
                        ingredient("White Onion", 0.05),
                        ingredient("Garlic", 0.01),
                        ingredient("Fresh Milk", 0.10),
                        ingredient("Unsalted Butter", 0.015)
                ),
                dish(
                        "Grilled Chicken with Jasmine Rice",
                        "Main Course",
                        "Grilled chicken breast served with fragrant jasmine rice.",
                        "https://images.unsplash.com/photo-1532550907401-a500c9a57435?w=600&q=80",
                        140.0,
                        ingredient("Chicken Breast", 0.20),
                        ingredient("Jasmine Rice", 0.18),
                        ingredient("Garlic", 0.005),
                        ingredient("Black Pepper", 1.0)
                ),
                dish(
                        "Garlic Butter Salmon",
                        "Main Course",
                        "Pan-seared salmon with garlic butter and jasmine rice.",
                        "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=600&q=80",
                        150.0,
                        ingredient("Salmon Fillet", 0.20),
                        ingredient("Unsalted Butter", 0.015),
                        ingredient("Garlic", 0.005),
                        ingredient("Black Pepper", 1.0),
                        ingredient("Jasmine Rice", 0.15)
                ),
                dish(
                        "Beef Tenderloin Steak",
                        "Main Course",
                        "Tender beef steak with garlic butter and black pepper.",
                        "https://images.unsplash.com/photo-1600891964092-4316c288032e?w=600&q=80",
                        160.0,
                        ingredient("Beef Tenderloin", 0.22),
                        ingredient("Unsalted Butter", 0.02),
                        ingredient("Garlic", 0.005),
                        ingredient("Black Pepper", 1.0)
                ),
                dish(
                        "Garlic Butter Prawns",
                        "Seafood",
                        "Tiger prawns sauteed in aromatic garlic butter.",
                        "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=600&q=80",
                        155.0,
                        ingredient("Tiger Prawns", 0.20),
                        ingredient("Unsalted Butter", 0.015),
                        ingredient("Garlic", 0.005),
                        ingredient("Black Pepper", 1.0)
                ),
                dish(
                        "BBQ Pork Ribs",
                        "Main Course",
                        "Slow-cooked pork ribs with house seasoning.",
                        "https://images.unsplash.com/photo-1544025162-d76694265947?w=600&q=80",
                        150.0,
                        ingredient("Pork Ribs", 0.30),
                        ingredient("Garlic", 0.008),
                        ingredient("Black Pepper", 1.0)
                )
        );

        menuItemRepository.saveAll(items);
        System.out.println("[MenuSeeder] Seeded " + items.size() + " menu items.");
    }

    private MenuItem dish(
            String name,
            String category,
            String description,
            String imageUrl,
            double profitMargin,
            RecipeIngredient... ingredients) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setCategory(category);
        item.setMenuCategory(categoryRepository.findByNameIgnoreCase(normalizeCategory(category))
                .orElseThrow(() -> new IllegalStateException("Missing menu category: " + category)));
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setProfitMarginPercent(profitMargin);
        item.setIsActive(true);
        item.replaceRecipe(Arrays.asList(ingredients));
        return item;
    }

    private String normalizeCategory(String category) {
        return "Seafood".equalsIgnoreCase(category) ? "Main Course" : category;
    }

    private RecipeIngredient ingredient(String inventoryName, double requiredQuantity) {
        InventoryItem inventory = inventoryRepository.findByItemNameIgnoreCase(inventoryName)
                .orElseThrow(() -> new IllegalStateException("Missing seeded inventory item: " + inventoryName));
        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setInventoryItem(inventory);
        ingredient.setRequiredQuantity(requiredQuantity);
        return ingredient;
    }
}
