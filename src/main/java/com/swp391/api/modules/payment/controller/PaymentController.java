package com.swp391.api.modules.payment.controller;

import com.swp391.api.modules.payment.config.VnpayProperties;
import com.swp391.api.modules.payment.dto.ApplyPromotionRequest;
import com.swp391.api.modules.payment.dto.InvoiceResponse;
import com.swp391.api.modules.payment.dto.PaymentRequest;
import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.VnpayIpnResponse;
import com.swp391.api.modules.payment.dto.VnpayReturnResult;
import com.swp391.api.modules.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final VnpayProperties vnpayProperties;

    public PaymentController(PaymentService paymentService, VnpayProperties vnpayProperties) {
        this.paymentService = paymentService;
        this.vnpayProperties = vnpayProperties;
    }

    @GetMapping("/orders/{orderId}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> getOrCreateInvoiceByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getOrCreateInvoiceByOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> issueInvoice(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getOrCreateInvoiceByOrder(orderId));
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getInvoice(invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/promotion")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> applyPromotion(
            @PathVariable Long invoiceId,
            @Valid @RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(paymentService.applyPromotion(invoiceId, request.getCode()));
    }

    @DeleteMapping("/invoices/{invoiceId}/promotion")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> removePromotion(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.removePromotion(invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentService.processPayment(invoiceId, request, httpRequest));
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<VnpayIpnResponse> vnpayIpn(HttpServletRequest request) {
        return ResponseEntity.ok(paymentService.handleVnpayIpn(extractParams(request)));
    }

    @GetMapping("/vnpay/return")
    public RedirectView vnpayReturn(HttpServletRequest request) {
        VnpayReturnResult result = paymentService.handleVnpayReturn(extractParams(request));
        String baseUrl = vnpayProperties.getFrontendReturnUrl() == null || vnpayProperties.getFrontendReturnUrl().isBlank()
                ? "http://localhost:5173/dashboard/orders-service"
                : vnpayProperties.getFrontendReturnUrl();
        String url = baseUrl
                + "?vnpayStatus=" + (result.success() ? "success" : "failed")
                + "&invoiceId=" + nullToEmpty(result.invoiceId())
                + "&orderId=" + nullToEmpty(result.orderId())
                + "&message=" + URLEncoder.encode(result.message(), StandardCharsets.UTF_8);
        return new RedirectView(url);
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
