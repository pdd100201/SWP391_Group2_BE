package com.swp391.api.modules.payment.controller;

import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.payment.dto.ApplyPromotionRequest;
import com.swp391.api.modules.payment.dto.BillResponse;
import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.SepayWebhookRequest;
import jakarta.validation.Valid;
import com.swp391.api.modules.payment.entity.Bill;
import com.swp391.api.modules.payment.entity.BillStatus;
import com.swp391.api.modules.payment.repository.BillRepository;
import com.swp391.api.modules.payment.service.PaymentService;
import com.swp391.api.modules.payment.service.SepayProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
public class PaymentController {
    private final PaymentService paymentService;
    private final SepayProperties sepayProperties;
    private final BillRepository billRepository;
    private final OrderService orderService;

    public PaymentController(
            PaymentService paymentService,
            SepayProperties sepayProperties,
            BillRepository billRepository,
            OrderService orderService) {
        this.paymentService = paymentService;
        this.sepayProperties = sepayProperties;
        this.billRepository = billRepository;
        this.orderService = orderService;
    }

    @GetMapping("/bills")
    public ResponseEntity<List<BillResponse>> getBills(
            @org.springframework.web.bind.annotation.RequestParam(name = "status", required = false) String status) {
        List<Bill> bills;
        if (StringUtils.hasText(status)) {
            BillStatus billStatus = BillStatus.valueOf(status.trim().toUpperCase());
            bills = billRepository.findByStatusOrderByPaidAtDesc(billStatus);
        } else {
            bills = billRepository.findAllByOrderByUpdatedAtDesc();
        }
        return ResponseEntity.ok(bills.stream().map(orderService::toBillResponse).toList());
    }

    @GetMapping("/bills/reservations/{reservationId}")
    public ResponseEntity<BillResponse> getReservationBill(@PathVariable Long reservationId) {
        paymentService.syncOpenReservationBill(reservationId);
        return billRepository.findByReservation_ReservationId(reservationId)
                .map(orderService::toBillResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    @PatchMapping("/bills/reservations/{reservationId}/promotion")
    public ResponseEntity<BillResponse> applyReservationPromotion(
            @PathVariable Long reservationId,
            @Valid @RequestBody ApplyPromotionRequest request) {
        paymentService.applyReservationPromotion(reservationId, request.getCode());
        return ResponseEntity.ok(getReservationBillBody(reservationId));
    }

    @DeleteMapping("/bills/reservations/{reservationId}/promotion")
    public ResponseEntity<BillResponse> removeReservationPromotion(@PathVariable Long reservationId) {
        paymentService.removeReservationPromotion(reservationId);
        return ResponseEntity.ok(getReservationBillBody(reservationId));
    }

    @PostMapping("/bills/reservations/{reservationId}/sepay")
    public ResponseEntity<BillResponse> createReservationSepayPayment(@PathVariable Long reservationId) {
        paymentService.createReservationSepayPayment(reservationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(getReservationBillBody(reservationId));
    }

    @PostMapping("/bills/reservations/{reservationId}/cash")
    public ResponseEntity<BillResponse> createReservationCashPayment(@PathVariable Long reservationId) {
        paymentService.createReservationCashPayment(reservationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(getReservationBillBody(reservationId));
    }

    @PostMapping("/bills/reservations/{reservationId}/cancel-payment")
    public ResponseEntity<BillResponse> cancelReservationPayment(@PathVariable Long reservationId) {
        paymentService.cancelReservationPayment(reservationId);
        return ResponseEntity.ok(getReservationBillBody(reservationId));
    }

    @GetMapping("/orders/{orderId}/latest")
    public ResponseEntity<PaymentResponse> getLatestPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getLatestPayment(orderId));
    }
    //SePay gọi webhook vào backend --> Nó kiểm tra API key rồi gọi service
    @PostMapping("/sepay/webhook")
    public ResponseEntity<Map<String, Boolean>> handleSepayWebhook(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookRequest request) {
        validateSepayAuthorization(authorization);
        return ResponseEntity.ok(paymentService.handleSepayWebhook(request));
    }

    private BillResponse getReservationBillBody(Long reservationId) {
        paymentService.syncOpenReservationBill(reservationId);
        return billRepository.findByReservation_ReservationId(reservationId)
                .map(orderService::toBillResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    private void validateSepayAuthorization(String authorization) {
        String apiKey = sepayProperties.getWebhookApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return;
        }
        if (!("Apikey " + apiKey).equals(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SePay webhook API key");
        }
    }
}
