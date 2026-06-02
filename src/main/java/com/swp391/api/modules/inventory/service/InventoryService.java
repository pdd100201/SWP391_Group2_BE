package com.swp391.api.modules.inventory.service;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;

import java.util.List;

public interface InventoryService {

    List<InventoryItemResponse> getAllItems();

    InventoryItemResponse getItemById(Long id);

    InventoryItemResponse createItem(InventoryItemRequest request);

    InventoryItemResponse updateQuantity(Long id, UpdateQuantityRequest request);

    InventoryItemResponse updateStatus(Long id, UpdateStatusRequest request);

    InventoryItemResponse toggleActive(Long id);

    List<InventoryItemResponse> searchItems(String keyword, String category, Boolean isActive);
}
