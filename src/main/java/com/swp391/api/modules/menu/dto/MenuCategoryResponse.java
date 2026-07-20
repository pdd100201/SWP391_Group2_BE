package com.swp391.api.modules.menu.dto;

// Lightweight category projection used by menu selectors.
public record MenuCategoryResponse(Long id, String name, String description, Boolean isActive) {
}
