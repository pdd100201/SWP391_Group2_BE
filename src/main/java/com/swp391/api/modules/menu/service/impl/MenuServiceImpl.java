package com.swp391.api.modules.menu.service.impl;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.entity.MenuCategory;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.service.MenuService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MenuServiceImpl implements MenuService {
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuServiceImpl(MenuItemRepository menuItemRepository, MenuCategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAll() {
        return menuItemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getById(Long id) { return toResponse(findMenuItem(id)); }

    @Override
    public MenuItemResponse create(MenuItemRequest request) {
        menuItemRepository.findByNameIgnoreCase(request.getName().trim()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish name already exists");
        });
        MenuItem item = new MenuItem();
        applyRequest(item, request);
        item.setIsActive(true);
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public MenuItemResponse update(Long id, MenuItemRequest request) {
        MenuItem item = findMenuItem(id);
        menuItemRepository.findByNameIgnoreCase(request.getName().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish name already exists"); });
        applyRequest(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public MenuItemResponse toggleActive(Long id) {
        MenuItem item = findMenuItem(id);
        item.setIsActive(!item.getIsActive());
        return toResponse(menuItemRepository.save(item));
    }

    private void applyRequest(MenuItem item, MenuItemRequest request) {
        item.setName(request.getName().trim());
        item.setMenuCategory(resolveCategory(request));
        item.setDescription(normalizeNullable(request.getDescription()));
        item.setImageUrl(normalizeNullable(request.getImageUrl()));
        item.setPrice(request.getPrice());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(item.getId());
        response.setName(item.getName());
        response.setCategory(item.getCategory());
        response.setCategoryId(item.getMenuCategory() == null ? null : item.getMenuCategory().getId());
        response.setDescription(item.getDescription());
        response.setImageUrl(item.getImageUrl());
        response.setPrice(item.getPrice() == null ? 0.0 : item.getPrice());
        response.setIsActive(item.getIsActive());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        response.setAvailability(Boolean.TRUE.equals(item.getIsActive()) ? "AVAILABLE" : "INACTIVE");
        return response;
    }

    private MenuItem findMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    private MenuCategory resolveCategory(MenuItemRequest request) {
        if (request.getCategoryId() != null) {
            return categoryRepository.findById(request.getCategoryId())
                    .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
        }
        return categoryRepository.findByNameIgnoreCase(request.getCategory().trim())
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
    }

    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
