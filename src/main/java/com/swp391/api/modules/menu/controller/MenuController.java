package com.swp391.api.modules.menu.controller;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.dto.ReservationRequest;
import com.swp391.api.modules.menu.dto.ReservationResponse;
import com.swp391.api.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAll() {
        return ResponseEntity.ok(menuService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(menuService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.toggleActive(id));
    }

    @PostMapping("/{id}/reservations")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable Long id,
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.reserve(id, request));
    }

    @PostMapping("/reservations/{reservationId}/serve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<ReservationResponse> serve(@PathVariable Long reservationId) {
        return ResponseEntity.ok(menuService.serve(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<ReservationResponse> release(@PathVariable Long reservationId) {
        return ResponseEntity.ok(menuService.release(reservationId));
    }
}
