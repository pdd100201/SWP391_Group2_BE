package com.swp391.api.modules.payment.service;

import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.SepayWebhookRequest;
import com.swp391.api.modules.payment.entity.Payment;
import java.util.Map;

/**
 * SePay payment operations for an order. The order remains the source of truth
 * for the complete payment lifecycle.
 */
public interface PaymentService {
    PaymentResponse createSepayPayment(Long orderId);

    PaymentResponse createCashPayment(Long orderId);

    PaymentResponse getLatestPayment(Long orderId);

    Map<String, Boolean> handleSepayWebhook(SepayWebhookRequest request);

    boolean isOrderPaid(Long orderId);

    Payment latestPaymentOrNull(Long orderId);

    PaymentResponse toResponse(Payment payment);
}
