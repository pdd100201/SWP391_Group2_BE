package com.swp391.api.modules.payment.dto;

public record PaymentResponse(
        InvoiceResponse invoice,
        String paymentUrl,
        String message) {
}
