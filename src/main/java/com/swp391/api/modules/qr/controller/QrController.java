package com.swp391.api.modules.qr.controller;

import com.swp391.api.modules.qr.dto.QrMenuResponse;
import com.swp391.api.modules.qr.dto.QrOrderRequest;
import com.swp391.api.modules.qr.dto.QrOrderResponse;
import com.swp391.api.modules.qr.dto.QrSessionResponse;
import com.swp391.api.modules.qr.service.QrService;
import jakarta.validation.Valid;
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
}
