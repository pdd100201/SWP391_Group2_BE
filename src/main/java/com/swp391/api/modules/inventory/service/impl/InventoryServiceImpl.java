package com.swp391.api.modules.inventory.service.impl;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;
import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.inventory.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // ─── Status Calculation ─────────────────────────────────────────────────────
    /**
     * Status priority:
     * 1. Manual override (statusOverride != null) → use override value
     * 2. Auto-calculate based on quantity thresholds:
     *    - OUT_OF_STOCK : quantity <= 0
     *    - LOW_STOCK    : 0 < quantity <= minimumQuantity
     *    - IN_STOCK     : quantity > minimumQuantity
     */
    private String computeStatus(InventoryItem item) {
        if (item.getStatusOverride() != null) return item.getStatusOverride();
        if (item.getQuantity() <= 0) return "OUT_OF_STOCK";
        if (item.getQuantity() <= item.getMinimumQuantity()) return "LOW_STOCK";
        return "IN_STOCK";
    }

    // ─── Entity → Response ───────────────────────────────────────────────────
    private InventoryItemResponse toResponse(InventoryItem item) {
        InventoryItemResponse response = new InventoryItemResponse();
        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setCategory(item.getCategory());
        response.setUnit(item.getUnit());
        response.setQuantity(item.getQuantity());
        response.setMinimumQuantity(item.getMinimumQuantity());
        response.setPricePerUnit(item.getPricePerUnit());
        response.setSupplier(item.getSupplier());
        response.setIsActive(item.getIsActive());
        response.setImageUrl(item.getImageUrl());
        response.setStatus(computeStatus(item));
        response.setIsStatusOverridden(item.getStatusOverride() != null);
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }

    // ─── Service Methods ─────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getAllItems() {
        return inventoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(Long id) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
        return toResponse(item);
    }

    @Override
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        inventoryRepository.findByItemNameIgnoreCase(request.getItemName())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Item name already exists");
                });

        InventoryItem item = new InventoryItem();
        item.setItemName(request.getItemName());
        item.setCategory(request.getCategory());
        item.setUnit(request.getUnit());
        item.setQuantity(request.getQuantity());
        item.setMinimumQuantity(request.getMinimumQuantity());
        item.setPricePerUnit(request.getPricePerUnit());
        item.setSupplier(request.getSupplier());
        item.setImageUrl(request.getImageUrl());
        item.setIsActive(true);

        InventoryItem saved = inventoryRepository.save(item);
        return toResponse(saved);
    }

    @Override
    public InventoryItemResponse updateQuantity(Long id, UpdateQuantityRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        item.setQuantity(request.getQuantity());
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    @Override
    public InventoryItemResponse updateStatus(Long id, UpdateStatusRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        String override = request.getStatusOverride();
        // Validate if non-null
        if (override != null && !override.equals("IN_STOCK")
                && !override.equals("LOW_STOCK")
                && !override.equals("OUT_OF_STOCK")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status. Must be IN_STOCK, LOW_STOCK, OUT_OF_STOCK, or null to reset.");
        }

        item.setStatusOverride(override); // null = reset to auto
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    @Override
    public InventoryItemResponse toggleActive(Long id) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        item.setIsActive(!item.getIsActive());
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> searchItems(String keyword, String category, Boolean isActive) {
        List<InventoryItem> results;

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasActive = isActive != null;

        if (hasKeyword && hasCategory && hasActive) {
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndCategoryIgnoreCaseAndIsActive(keyword, category, isActive);
        } else if (hasKeyword && hasCategory) {
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndCategoryIgnoreCase(keyword, category);
        } else if (hasKeyword && hasActive) {
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndIsActive(keyword, isActive);
        } else if (hasKeyword) {
            results = inventoryRepository.findByItemNameContainingIgnoreCase(keyword);
        } else if (hasCategory) {
            results = inventoryRepository.findByCategoryIgnoreCase(category);
        } else if (hasActive) {
            results = inventoryRepository.findByIsActive(isActive);
        } else {
            results = inventoryRepository.findAll();
        }

        return results.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
