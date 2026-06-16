package com.swp391.api.modules.inventory.repository;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByItemNameIgnoreCase(String itemName);

    List<InventoryItem> findByItemNameContainingIgnoreCase(String keyword);

    List<InventoryItem> findByCategoryIgnoreCase(String category);

    List<InventoryItem> findByIsActive(Boolean isActive);

    List<InventoryItem> findByItemNameContainingIgnoreCaseAndCategoryIgnoreCase(String keyword, String category);

    List<InventoryItem> findByItemNameContainingIgnoreCaseAndIsActive(String keyword, Boolean isActive);

    List<InventoryItem> findByItemNameContainingIgnoreCaseAndCategoryIgnoreCaseAndIsActive(
            String keyword, String category, Boolean isActive);
}
