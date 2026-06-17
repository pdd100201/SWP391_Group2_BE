package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    @Override
    @EntityGraph(attributePaths = {"recipeIngredients", "recipeIngredients.inventoryItem"})
    List<MenuItem> findAll();

    @Override
    @EntityGraph(attributePaths = {"recipeIngredients", "recipeIngredients.inventoryItem"})
    Optional<MenuItem> findById(Long id);

    Optional<MenuItem> findByNameIgnoreCase(String name);
}
