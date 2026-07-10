package com.swp391.api.modules.order.controller;

import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.ApplyPromotionRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.dto.UpdateOrderItemStatusRequest;
import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
    // Staff-facing order API. The controller maps routes to service actions only.
    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        // Opens an order from a reservation that already has an assigned table.
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        // active=true is used by the order workspace to show only OPEN orders.
        return ResponseEntity.ok(orderService.getOrders(activeOnly));
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
        // Adds a draft item; inventory is deducted only when draft items are submitted.
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

    @PostMapping("/{orderId}/submit")
    public ResponseEntity<OrderResponse> submit(@PathVariable Long orderId) {
        // Converts all DRAFT items to CONFIRMED and deducts inventory snapshots.
        return ResponseEntity.ok(orderService.submit(orderId));
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    public ResponseEntity<OrderResponse> updateItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemStatusRequest request) {
        // Drives kitchen/service workflow: CONFIRMED -> PREPARING -> READY -> SERVED.
        return ResponseEntity.ok(orderService.updateItemStatus(orderId, itemId, request.getStatus()));
    }

    @PatchMapping("/{orderId}/close")
    public ResponseEntity<OrderResponse> close(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.close(orderId));
    }

    @PatchMapping("/{orderId}/sepay-payment")
    @PreAuthorize("permitAll()")
    public ResponseEntity<OrderResponse> createSepayPayment(@PathVariable Long orderId) {
        paymentService.createSepayPayment(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.getById(orderId));
    }

    @PatchMapping("/{orderId}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<?> createOrderPayment(@PathVariable Long orderId) {
        try {
            paymentService.createSepayPayment(orderId);
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.getById(orderId));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", ex.getMessage() == null ? "Payment failed" : ex.getMessage(),
                    "errorClass", ex.getClass().getName()
            ));
        }
    }

    @PatchMapping("/{orderId}/payment-test")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<Map<String, Object>> testOrderPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(Map.of("ok", true, "orderId", orderId));
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
