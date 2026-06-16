package com.swp391.api.modules.inventory.controller;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;
import com.swp391.api.modules.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** GET /api/inventory */
    @GetMapping
    public ResponseEntity<List<InventoryItemResponse>> getAllItems() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    /** GET /api/inventory/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> getItemById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getItemById(id));
    }

    /** POST /api/inventory */
    @PostMapping
    public ResponseEntity<InventoryItemResponse> createItem(@Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createItem(request));
    }

    /** PUT /api/inventory/{id}/quantity */
    @PutMapping("/{id}/quantity")
    public ResponseEntity<InventoryItemResponse> updateQuantity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuantityRequest request) {
        return ResponseEntity.ok(inventoryService.updateQuantity(id, request));
    }

    /**
     * PUT /api/inventory/{id}/status
     * Pass { "statusOverride": "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" } to override.
     * Pass { "statusOverride": null } to reset back to auto-calculation.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<InventoryItemResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(inventoryService.updateStatus(id, request));
    }

    /** PATCH /api/inventory/{id}/toggle-active */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<InventoryItemResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.toggleActive(id));
    }

    /** GET /api/inventory/search?keyword=...&category=...&isActive=... */
    @GetMapping("/search")
    public ResponseEntity<List<InventoryItemResponse>> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return ResponseEntity.ok(inventoryService.searchItems(keyword, category, isActive));
    }
}

