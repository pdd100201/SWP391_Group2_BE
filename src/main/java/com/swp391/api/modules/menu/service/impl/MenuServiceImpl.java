package com.swp391.api.modules.menu.service.impl;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.dto.*;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.entity.MenuReservation;
import com.swp391.api.modules.menu.entity.MenuReservationIngredient;
import com.swp391.api.modules.menu.entity.RecipeIngredient;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuReservationRepository;
import com.swp391.api.modules.menu.service.MenuService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class MenuServiceImpl implements MenuService {
    // Small tolerance for double-based inventory math.
    private static final double EPSILON = 0.000001;

    private final MenuItemRepository menuItemRepository;
    private final MenuReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuServiceImpl(
            MenuItemRepository menuItemRepository,
            MenuReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            MenuCategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAll() {
        // Every response includes computed cost, suggested price, stock, and availability.
        return menuItemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getById(Long id) {
        return toResponse(findMenuItem(id));
    }

    @Override
    public MenuItemResponse create(MenuItemRequest request) {
        // Dish names are unique so staff cannot accidentally create duplicate menu items.
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
        // Updating a dish also replaces or updates its recipe ingredients.
        MenuItem item = findMenuItem(id);
        menuItemRepository.findByNameIgnoreCase(request.getName().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish name already exists");
                });

        applyRequest(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public MenuItemResponse toggleActive(Long id) {
        // Inactive dishes remain in history but are hidden from ordering flows.
        MenuItem item = findMenuItem(id);
        item.setIsActive(!item.getIsActive());
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public ReservationResponse reserve(Long menuItemId, ReservationRequest request) {
        // Menu reservation locks ingredient quantities without deducting physical stock yet.
        MenuItem item = findMenuItem(menuItemId);
        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish is inactive");
        }

        List<RecipeIngredient> recipe = item.getRecipeIngredients().stream()
                .sorted(Comparator.comparing(ingredient -> ingredient.getInventoryItem().getId()))
                .toList();
        if (recipe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish has no recipe");
        }

        List<LockedRequirement> requirements = new ArrayList<>();
        for (RecipeIngredient recipeIngredient : recipe) {
            // Lock each inventory row before checking/deducting reserved quantity.
            InventoryItem inventory = lockInventory(recipeIngredient.getInventoryItem().getId());
            double required = recipeIngredient.getRequiredQuantity() * request.getServings();
            double available = inventory.getQuantity() - inventory.getReservedQuantity();

            if (!Boolean.TRUE.equals(inventory.getIsActive()) || available + EPSILON < required) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Not enough available inventory for " + inventory.getItemName()
                );
            }
            requirements.add(new LockedRequirement(inventory, required));
        }

        MenuReservation reservation = new MenuReservation();
        reservation.setMenuItemId(item.getId());
        reservation.setMenuItemName(item.getName());
        reservation.setServings(request.getServings());
        reservation.setReferenceCode(normalizeNullable(request.getReferenceCode()));
        reservation.setStatus("RESERVED");

        for (LockedRequirement requirement : requirements) {
            InventoryItem inventory = requirement.inventory();
            inventory.setReservedQuantity(inventory.getReservedQuantity() + requirement.quantity());

            MenuReservationIngredient snapshot = new MenuReservationIngredient();
            snapshot.setInventoryItem(inventory);
            snapshot.setInventoryItemName(inventory.getItemName());
            snapshot.setUnit(inventory.getUnit());
            snapshot.setRequiredQuantity(requirement.quantity());
            reservation.addIngredient(snapshot);
        }

        return toReservationResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    public ReservationResponse serve(Long reservationId) {
        // Serving a reservation converts reserved stock into actual inventory consumption.
        MenuReservation reservation = findReservation(reservationId);
        requireReserved(reservation);

        List<MenuReservationIngredient> ingredients = reservation.getIngredients().stream()
                .sorted(Comparator.comparing(ingredient -> ingredient.getInventoryItem().getId()))
                .toList();

        for (MenuReservationIngredient snapshot : ingredients) {
            InventoryItem inventory = lockInventory(snapshot.getInventoryItem().getId());
            double required = snapshot.getRequiredQuantity();
            if (inventory.getQuantity() + EPSILON < required) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Physical inventory is insufficient for " + inventory.getItemName()
                );
            }
            inventory.setQuantity(inventory.getQuantity() - required);
            inventory.setReservedQuantity(Math.max(0.0, inventory.getReservedQuantity() - required));
        }

        reservation.setStatus("SERVED");
        return toReservationResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    public ReservationResponse release(Long reservationId) {
        // Releasing gives reserved stock back when the dish is not served.
        MenuReservation reservation = findReservation(reservationId);
        requireReserved(reservation);

        List<MenuReservationIngredient> ingredients = reservation.getIngredients().stream()
                .sorted(Comparator.comparing(ingredient -> ingredient.getInventoryItem().getId()))
                .toList();

        for (MenuReservationIngredient snapshot : ingredients) {
            InventoryItem inventory = lockInventory(snapshot.getInventoryItem().getId());
            inventory.setReservedQuantity(
                    Math.max(0.0, inventory.getReservedQuantity() - snapshot.getRequiredQuantity())
            );
        }

        reservation.setStatus("RELEASED");
        return toReservationResponse(reservationRepository.saveAndFlush(reservation));
    }

    private void applyRequest(MenuItem item, MenuItemRequest request) {
        // Normalize the incoming form and synchronize the recipe child collection.
        Set<Long> inventoryIds = new HashSet<>();
        List<IngredientDefinition> definitions = new ArrayList<>();

        for (RecipeIngredientRequest ingredientRequest : request.getIngredients()) {
            // A recipe cannot contain the same inventory item twice.
            if (!inventoryIds.add(ingredientRequest.getInventoryItemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe contains duplicate ingredients");
            }

            InventoryItem inventory = inventoryRepository.findById(ingredientRequest.getInventoryItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Inventory item not found: " + ingredientRequest.getInventoryItemId()
                    ));

            definitions.add(new IngredientDefinition(inventory, ingredientRequest.getRequiredQuantity()));
        }

        item.setName(request.getName().trim());
        item.setMenuCategory(resolveCategory(request));
        item.setDescription(normalizeNullable(request.getDescription()));
        item.setImageUrl(normalizeNullable(request.getImageUrl()));
        item.setProfitMarginPercent(request.getProfitMarginPercent());

        if (item.getId() == null) {
            List<RecipeIngredient> recipe = definitions.stream().map(definition -> {
                RecipeIngredient ingredient = new RecipeIngredient();
                ingredient.setInventoryItem(definition.inventory());
                ingredient.setRequiredQuantity(definition.quantity());
                return ingredient;
            }).toList();
            item.replaceRecipe(recipe);
            return;
        }

        item.getRecipeIngredients().removeIf(existing ->
                !inventoryIds.contains(existing.getInventoryItem().getId())
        );
        for (IngredientDefinition definition : definitions) {
            RecipeIngredient existing = item.getRecipeIngredients().stream()
                    .filter(ingredient ->
                            ingredient.getInventoryItem().getId().equals(definition.inventory().getId())
                    )
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.setRequiredQuantity(definition.quantity());
            } else {
                RecipeIngredient ingredient = new RecipeIngredient();
                ingredient.setMenuItem(item);
                ingredient.setInventoryItem(definition.inventory());
                ingredient.setRequiredQuantity(definition.quantity());
                item.getRecipeIngredients().add(ingredient);
            }
        }
    }

    private MenuItemResponse toResponse(MenuItem item) {
        // Builds the rich DTO used by FE: pricing, stock, availability, and recipe details.
        MenuItemResponse response = new MenuItemResponse();
        response.setId(item.getId());
        response.setName(item.getName());
        response.setCategory(item.getCategory());
        response.setCategoryId(item.getMenuCategory() == null ? null : item.getMenuCategory().getId());
        response.setDescription(item.getDescription());
        response.setImageUrl(item.getImageUrl());
        response.setProfitMarginPercent(item.getProfitMarginPercent());
        response.setIsActive(item.getIsActive());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        double foodCost = 0.0;
        boolean costComplete = true;
        int availableServings = Integer.MAX_VALUE;
        boolean lowStock = false;
        List<String> blocking = new ArrayList<>();
        List<RecipeIngredientResponse> ingredients = new ArrayList<>();

        for (RecipeIngredient recipeIngredient : item.getRecipeIngredients()) {
            // Available servings is limited by the scarcest active ingredient.
            InventoryItem inventory = recipeIngredient.getInventoryItem();
            double available = Math.max(0.0, inventory.getQuantity() - inventory.getReservedQuantity());
            int servings = (int) Math.floor((available + EPSILON) / recipeIngredient.getRequiredQuantity());
            availableServings = Math.min(availableServings, servings);

            if (!Boolean.TRUE.equals(inventory.getIsActive()) || servings <= 0) {
                blocking.add(inventory.getItemName());
            }
            if (available <= inventory.getMinimumQuantity() || servings <= 5) {
                lowStock = true;
            }

            Double price = inventory.getPricePerUnit();
            if (price == null) costComplete = false;
            double ingredientCost = price == null ? 0.0 : price * recipeIngredient.getRequiredQuantity();
            foodCost += ingredientCost;

            RecipeIngredientResponse ingredientResponse = new RecipeIngredientResponse();
            ingredientResponse.setInventoryItemId(inventory.getId());
            ingredientResponse.setInventoryItemName(inventory.getItemName());
            ingredientResponse.setUnit(inventory.getUnit());
            ingredientResponse.setRequiredQuantity(recipeIngredient.getRequiredQuantity());
            ingredientResponse.setInventoryQuantity(inventory.getQuantity());
            ingredientResponse.setReservedQuantity(inventory.getReservedQuantity());
            ingredientResponse.setAvailableQuantity(available);
            ingredientResponse.setPricePerUnit(price);
            ingredientResponse.setIngredientCost(ingredientCost);
            ingredientResponse.setInventoryActive(inventory.getIsActive());
            ingredients.add(ingredientResponse);
        }

        if (item.getRecipeIngredients().isEmpty()) {
            availableServings = 0;
            blocking.add("Recipe is empty");
        }

        String availability;
        // Availability is derived, not stored: inactive, out of stock, limited, or available.
        if (!Boolean.TRUE.equals(item.getIsActive())) {
            availability = "INACTIVE";
        } else if (!blocking.isEmpty()) {
            availability = "OUT_OF_STOCK";
        } else if (lowStock) {
            availability = "LIMITED";
        } else {
            availability = "AVAILABLE";
        }

        double rawSuggestedPrice = foodCost * (1 + item.getProfitMarginPercent() / 100.0);
        double suggestedPrice = Math.ceil(rawSuggestedPrice / 1000.0) * 1000.0;
        response.setFoodCost(foodCost);
        response.setSuggestedPrice(suggestedPrice);
        response.setCostComplete(costComplete);
        response.setAvailability(availability);
        response.setAvailableServings(Math.max(0, availableServings));
        response.setBlockingIngredients(blocking);
        response.setIngredients(ingredients);
        return response;
    }

    private ReservationResponse toReservationResponse(MenuReservation reservation) {
        ReservationResponse response = new ReservationResponse();
        response.setId(reservation.getId());
        response.setMenuItemId(reservation.getMenuItemId());
        response.setMenuItemName(reservation.getMenuItemName());
        response.setServings(reservation.getServings());
        response.setStatus(reservation.getStatus());
        response.setReferenceCode(reservation.getReferenceCode());
        response.setCreatedAt(reservation.getCreatedAt());
        response.setUpdatedAt(reservation.getUpdatedAt());
        return response;
    }

    private MenuItem findMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    private MenuReservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    private InventoryItem lockInventory(Long id) {
        return inventoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Inventory item no longer exists"));
    }

    private void requireReserved(MenuReservation reservation) {
        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation has already been " + reservation.getStatus().toLowerCase()
            );
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private com.swp391.api.modules.menu.entity.MenuCategory resolveCategory(MenuItemRequest request) {
        // Prefer categoryId from the form, but keep category name fallback for older clients.
        if (request.getCategoryId() != null) {
            return categoryRepository.findById(request.getCategoryId())
                    .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
        }
        return categoryRepository.findByNameIgnoreCase(request.getCategory().trim())
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
    }

    private record LockedRequirement(InventoryItem inventory, double quantity) {}
    private record IngredientDefinition(InventoryItem inventory, double quantity) {}
}
