package com.swp391.api.modules.order.controller;

import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.ApplyPromotionRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderGroupResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.dto.UpdateOrderItemStatusRequest;
import com.swp391.api.modules.order.dto.VoidOrderItemRequest;
import com.swp391.api.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderGroupResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createTableOrders(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(orderService.getOrders(activeOnly));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<OrderGroupResponse>> getGroups(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(orderService.getGroups(activeOnly));
    }

    @GetMapping("/groups/{reservationId}")
    public ResponseEntity<OrderGroupResponse> getGroup(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.getGroup(reservationId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getById(orderId));
    }

    @GetMapping("/by-reservation/{reservationId}")
    public ResponseEntity<OrderResponse> getByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.getByReservation(reservationId));
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<OrderResponse> addItem(
            @PathVariable Long orderId, @Valid @RequestBody AddOrderItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addItem(orderId, request));
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        return ResponseEntity.ok(orderService.updateItem(orderId, itemId, request));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> removeItem(@PathVariable Long orderId, @PathVariable Long itemId) {
        return ResponseEntity.ok(orderService.removeItem(orderId, itemId));
    }

    @PostMapping("/{orderId}/items/{itemId}/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<OrderResponse> voidServedItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody VoidOrderItemRequest request) {
        return ResponseEntity.ok(orderService.voidServedItem(orderId, itemId, request));
    }

    @PostMapping("/{orderId}/submit")
    public ResponseEntity<OrderResponse> submit(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.submit(orderId));
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    public ResponseEntity<OrderResponse> updateItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemStatusRequest request) {
        return ResponseEntity.ok(orderService.updateItemStatus(orderId, itemId, request.getStatus()));
    }

    @PostMapping("/{orderId}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<OrderResponse> createSepayPayment(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createSepayPayment(orderId));
    }

    @PostMapping("/{orderId}/payment/cash")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<OrderResponse> createCashPayment(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createCashPayment(orderId));
    }

    @PatchMapping("/{orderId}/close")
    public ResponseEntity<OrderResponse> close(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.close(orderId));
    }

    @PatchMapping("/reservations/{reservationId}/complete")
    public ResponseEntity<OrderGroupResponse> completeReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.completeReservation(reservationId));
    }

    @PatchMapping("/{orderId}/promotion")
    public ResponseEntity<OrderResponse> applyPromotion(
            @PathVariable Long orderId,
            @Valid @RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(orderService.applyPromotion(orderId, request.getCode()));
    }

    @DeleteMapping("/{orderId}/promotion")
    public ResponseEntity<OrderResponse> removePromotion(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.removePromotion(orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }
}
