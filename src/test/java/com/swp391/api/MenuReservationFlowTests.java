package com.swp391.api;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.dto.RecipeIngredientRequest;
import com.swp391.api.modules.menu.dto.ReservationRequest;
import com.swp391.api.modules.menu.dto.ReservationResponse;
import com.swp391.api.modules.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class MenuReservationFlowTests {

    @Autowired
    private MenuService menuService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void servingReservationDeductsPhysicalInventoryOnlyAtServedState() {
        MenuItemResponse salad = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();
        Long lettuceId = salad.getIngredients().stream()
                .filter(ingredient -> ingredient.getInventoryItemName().equals("Lettuce"))
                .findFirst()
                .orElseThrow()
                .getInventoryItemId();
        double originalLettuceQuantity = salad.getIngredients().stream()
                .filter(ingredient -> ingredient.getInventoryItemName().equals("Lettuce"))
                .findFirst()
                .orElseThrow()
                .getRequiredQuantity();

        InventoryItem before = inventoryRepository.findById(lettuceId).orElseThrow();
        double physicalBefore = before.getQuantity();
        double reservedBefore = before.getReservedQuantity();

        ReservationRequest request = new ReservationRequest();
        request.setServings(1);
        request.setReferenceCode("TEST-SERVED-FLOW");
        ReservationResponse reservation = menuService.reserve(salad.getId(), request);

        InventoryItem afterReserve = inventoryRepository.findById(lettuceId).orElseThrow();
        assertEquals(physicalBefore, afterReserve.getQuantity(), 0.000001);
        assertEquals(reservedBefore + originalLettuceQuantity, afterReserve.getReservedQuantity(), 0.000001);

        MenuItemRequest updatedRecipe = new MenuItemRequest();
        updatedRecipe.setName(salad.getName());
        updatedRecipe.setCategory(salad.getCategory());
        updatedRecipe.setDescription(salad.getDescription());
        updatedRecipe.setImageUrl(salad.getImageUrl());
        updatedRecipe.setProfitMarginPercent(salad.getProfitMarginPercent());
        List<RecipeIngredientRequest> ingredients = salad.getIngredients().stream().map(ingredient -> {
            RecipeIngredientRequest recipeIngredient = new RecipeIngredientRequest();
            recipeIngredient.setInventoryItemId(ingredient.getInventoryItemId());
            recipeIngredient.setRequiredQuantity(
                    ingredient.getInventoryItemName().equals("Lettuce")
                            ? 0.30
                            : ingredient.getRequiredQuantity()
            );
            return recipeIngredient;
        }).toList();
        updatedRecipe.setIngredients(ingredients);
        menuService.update(salad.getId(), updatedRecipe);

        ReservationResponse served = menuService.serve(reservation.getId());
        InventoryItem afterServe = inventoryRepository.findById(lettuceId).orElseThrow();

        assertEquals("SERVED", served.getStatus());
        // Reservation keeps the original recipe snapshot even after the recipe changes.
        assertEquals(physicalBefore - originalLettuceQuantity, afterServe.getQuantity(), 0.000001);
        assertEquals(reservedBefore, afterServe.getReservedQuantity(), 0.000001);
    }
}
