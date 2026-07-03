package com.swp391.api.modules.menu.controller;

import com.swp391.api.modules.menu.dto.MenuCategoryResponse;
import com.swp391.api.modules.menu.entity.MenuCategory;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu-categories")
public class MenuCategoryController {
    // Read-only endpoint for category dropdowns in the menu management form.
    private final MenuCategoryRepository categoryRepository;

    public MenuCategoryController(MenuCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<MenuCategoryResponse>> getActiveCategories() {
        // The UI only needs active categories, sorted consistently for selectors.
        return ResponseEntity.ok(categoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList());
    }

    private MenuCategoryResponse toResponse(MenuCategory category) {
        return new MenuCategoryResponse(
                category.getId(), category.getName(), category.getDescription(), category.getIsActive());
    }
}
