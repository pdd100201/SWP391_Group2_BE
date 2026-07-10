package com.swp391.api.modules.qr.controller;

import com.swp391.api.modules.qr.dto.QrAccessTokenResponse;
import com.swp391.api.modules.qr.dto.QrAddItemRequest;
import com.swp391.api.modules.qr.dto.QrMenuResponse;
import com.swp391.api.modules.qr.dto.QrOrderRequest;
import com.swp391.api.modules.qr.dto.QrOrderResponse;
import com.swp391.api.modules.qr.dto.QrOrderSummaryResponse;
import com.swp391.api.modules.qr.dto.QrSessionResponse;
import com.swp391.api.modules.qr.dto.QrUpdateItemRequest;
import com.swp391.api.modules.qr.dto.QrUpdateStatusRequest;
import com.swp391.api.modules.qr.service.QrService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final QrService qrService;

    public QrController(QrService qrService) {
        this.qrService = qrService;
    }

    @PostMapping("/session/{tableId}")
    public ResponseEntity<QrSessionResponse> createSession(@PathVariable Long tableId) {
        return ResponseEntity.ok(qrService.createSession(tableId));
    }

    @GetMapping("/menu")
    public ResponseEntity<QrMenuResponse> getMenu() {
        return ResponseEntity.ok(qrService.getMenu());
    }

    @PostMapping("/order")
    public ResponseEntity<QrOrderResponse> createOrder(@Valid @RequestBody QrOrderRequest request) {
        return ResponseEntity.ok(qrService.createOrder(request));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<QrOrderResponse> getOrderStatus(@PathVariable Long orderId) {
        return ResponseEntity.ok(qrService.getOrderStatus(orderId));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<QrOrderSummaryResponse>> getAllOrders() {
        return ResponseEntity.ok(qrService.getAllOrders());
    }

    @GetMapping("/table/{tableId}/order-token")
    public ResponseEntity<QrAccessTokenResponse> getTableOrderToken(@PathVariable Long tableId) {
        return qrService.getAccessTokenForTable(tableId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/table/{tableId}/active-order")
    public ResponseEntity<QrOrderResponse> getActiveOrder(@PathVariable Long tableId) {
        return qrService.getActiveOrder(tableId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/{orderId}/items")
    public ResponseEntity<QrOrderResponse> addItem(
            @PathVariable Long orderId,
            @RequestBody QrAddItemRequest request) {
        return ResponseEntity.ok(qrService.addItem(orderId, request.getMenuItemId(), request.getQuantity()));
    }

    @PatchMapping("/orders/{orderId}/items/{itemId}")
    public ResponseEntity<QrOrderResponse> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody QrUpdateItemRequest request) {
        return ResponseEntity.ok(qrService.updateItem(orderId, itemId, request.getQuantity(), request.getNote()));
    }

    @DeleteMapping("/orders/{orderId}/items/{itemId}")
    public ResponseEntity<QrOrderResponse> removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(qrService.removeItem(orderId, itemId));
    }

    @PostMapping("/orders/{orderId}/submit")
    public ResponseEntity<QrOrderResponse> submitOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(qrService.submitOrder(orderId));
    }

    @PatchMapping("/orders/{orderId}/items/{itemId}/status")
    public ResponseEntity<QrOrderResponse> updateItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody QrUpdateStatusRequest request) {
        return ResponseEntity.ok(qrService.updateItemStatus(orderId, itemId, request.getStatus()));
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<QrOrderResponse> closeOrder(
            @PathVariable Long orderId,
            @RequestBody QrUpdateStatusRequest request) {
        if (!"CLOSED".equals(request.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(qrService.closeOrder(orderId));
    }
}
