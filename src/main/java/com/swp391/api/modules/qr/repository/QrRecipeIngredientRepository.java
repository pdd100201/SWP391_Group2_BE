package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrRecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QrRecipeIngredientRepository extends JpaRepository<QrRecipeIngredient, Long> {

    // Bulk: [menu_item_id, foodCost] cho nhiều món — dùng trong getMenu()
    @Query(value =
        "SELECT ri.menu_item_id, SUM(ri.required_quantity * ii.price_per_unit) " +
        "FROM inventory_linked_recipe_ingredients ri " +
        "JOIN inventory_items ii ON ri.inventory_item_id = ii.id " +
        "WHERE ri.menu_item_id IN :menuItemIds " +
        "GROUP BY ri.menu_item_id",
        nativeQuery = true)
    List<Object[]> findFoodCostsByMenuItemIds(@Param("menuItemIds") Collection<Long> menuItemIds);

    // Single: foodCost cho một món — dùng trong createOrder()
    @Query(value =
        "SELECT SUM(ri.required_quantity * ii.price_per_unit) " +
        "FROM inventory_linked_recipe_ingredients ri " +
        "JOIN inventory_items ii ON ri.inventory_item_id = ii.id " +
        "WHERE ri.menu_item_id = :menuItemId",
        nativeQuery = true)
    Double findFoodCostByMenuItemId(@Param("menuItemId") Long menuItemId);

    // Bulk: [menu_item_id, canServe] — số phần tối đa có thể phục vụ dựa trên tồn kho
    @Query(value =
        "SELECT ri.menu_item_id, FLOOR(MIN(ii.quantity / ri.required_quantity)) " +
        "FROM inventory_linked_recipe_ingredients ri " +
        "JOIN inventory_items ii ON ri.inventory_item_id = ii.id " +
        "WHERE ri.menu_item_id IN :menuItemIds " +
        "GROUP BY ri.menu_item_id",
        nativeQuery = true)
    List<Object[]> findCanServeByMenuItemIds(@Param("menuItemIds") Collection<Long> menuItemIds);
}
