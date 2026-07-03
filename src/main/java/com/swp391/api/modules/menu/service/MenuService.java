package com.swp391.api.modules.menu.service;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.dto.ReservationRequest;
import com.swp391.api.modules.menu.dto.ReservationResponse;

import java.util.List;

// Business contract for menu management and inventory-linked recipe operations.
public interface MenuService {
    List<MenuItemResponse> getAll();
    MenuItemResponse getById(Long id);
    MenuItemResponse create(MenuItemRequest request);
    MenuItemResponse update(Long id, MenuItemRequest request);
    MenuItemResponse toggleActive(Long id);
    ReservationResponse reserve(Long menuItemId, ReservationRequest request);
    ReservationResponse serve(Long reservationId);
    ReservationResponse release(Long reservationId);
}
