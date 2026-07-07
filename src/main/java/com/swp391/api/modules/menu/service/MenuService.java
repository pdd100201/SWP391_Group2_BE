package com.swp391.api.modules.menu.service;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;

import java.util.List;

public interface MenuService {
    List<MenuItemResponse> getAll();
    MenuItemResponse getById(Long id);
    MenuItemResponse create(MenuItemRequest request);
    MenuItemResponse update(Long id, MenuItemRequest request);
    MenuItemResponse toggleActive(Long id);
}
