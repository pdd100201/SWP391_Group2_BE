package com.swp391.api.modules.order.controller;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-access/{token}")
public class PublicOrderController {
    // Public customer-facing order API, protected by an unguessable order token.
    private final OrderService orderService;

    public PublicOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String token) {
        // Lets guests reload their current order without staff credentials.
        return ResponseEntity.ok(orderService.getByToken(token));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable String token) {
        return ResponseEntity.ok(orderService.getPublicMenu(token));
    }

    @PostMapping("/items")
    public ResponseEntity<OrderResponse> addItem(
            @PathVariable String token, @Valid @RequestBody AddOrderItemRequest request) {
        // Guest-added items stay DRAFT until the guest submits them.
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addPublicItem(token, request));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<OrderResponse> updateItem(
            @PathVariable String token,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        return ResponseEntity.ok(orderService.updatePublicItem(token, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<OrderResponse> removeItem(@PathVariable String token, @PathVariable Long itemId) {
        return ResponseEntity.ok(orderService.removePublicItem(token, itemId));
    }

    @PostMapping("/submit")
    public ResponseEntity<OrderResponse> submit(@PathVariable String token) {
        return ResponseEntity.ok(orderService.submitPublic(token));
    }
}
