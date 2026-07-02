package com.swp391.api.modules.payment.dto;

public record VnpayReturnResult(
        Long invoiceId,
        Long orderId,
        boolean success,
        String message) {
}
