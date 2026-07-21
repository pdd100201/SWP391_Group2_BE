package com.swp391.api.modules.payment.controller;

import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.payment.dto.BillResponse;
import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.SepayWebhookRequest;
import com.swp391.api.modules.payment.entity.Bill;
import com.swp391.api.modules.payment.entity.BillStatus;
import com.swp391.api.modules.payment.repository.BillRepository;
import com.swp391.api.modules.payment.service.PaymentService;
import com.swp391.api.modules.payment.service.SepayProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
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
        return billRepository.findByReservation_ReservationId(reservationId)
                .map(orderService::toBillResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    @GetMapping("/orders/{orderId}/latest")
    public ResponseEntity<PaymentResponse> getLatestPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getLatestPayment(orderId));
    }

    @PostMapping("/sepay/webhook")
    public ResponseEntity<Map<String, Boolean>> handleSepayWebhook(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookRequest request) {
        validateSepayAuthorization(authorization);
        return ResponseEntity.ok(paymentService.handleSepayWebhook(request));
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
