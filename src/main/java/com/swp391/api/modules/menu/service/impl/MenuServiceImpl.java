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
import java.util.List;

@Service
@Transactional
public class MenuServiceImpl implements MenuService {
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
        return menuItemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getById(Long id) {
        return toResponse(findMenuItem(id));
    }

    @Override
    public MenuItemResponse create(MenuItemRequest request) {
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
        MenuItem item = findMenuItem(id);
        item.setIsActive(!item.getIsActive());
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public ReservationResponse reserve(Long menuItemId, ReservationRequest request) {
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
        item.setName(request.getName().trim());
        item.setMenuCategory(resolveCategory(request));
        item.setDescription(normalizeNullable(request.getDescription()));
        item.setImageUrl(normalizeNullable(request.getImageUrl()));
        item.setPrice(request.getPrice());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(item.getId());
        response.setName(item.getName());
        response.setCategory(item.getCategory());
        response.setCategoryId(item.getMenuCategory() == null ? null : item.getMenuCategory().getId());
        response.setDescription(item.getDescription());
        response.setImageUrl(item.getImageUrl());
        response.setPrice(item.getPrice() == null ? 0.0 : item.getPrice());
        response.setIsActive(item.getIsActive());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        response.setAvailability(Boolean.TRUE.equals(item.getIsActive()) ? "AVAILABLE" : "INACTIVE");
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
}
