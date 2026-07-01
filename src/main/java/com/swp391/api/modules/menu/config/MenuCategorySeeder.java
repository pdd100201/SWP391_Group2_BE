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

@Component
@Order(3)
public class MenuCategorySeeder implements CommandLineRunner {
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
        for (String name : DEFAULT_CATEGORIES) {
            categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                MenuCategory category = new MenuCategory();
                category.setName(name);
                category.setDescription("Menu category: " + name);
                category.setIsActive(true);
                return categoryRepository.save(category);
            });
        }

        for (MenuItem item : menuItemRepository.findAll()) {
            if (item.getMenuCategory() != null) continue;
            String target = "Appetizer".equalsIgnoreCase(item.getCategory())
                    ? "Appetizer"
                    : "Main Course";
            item.setMenuCategory(categoryRepository.findByNameIgnoreCase(target).orElseThrow());
        }
    }
}
