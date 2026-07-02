package com.swp391.api.modules.payment.dto;

import com.swp391.api.modules.payment.entity.PaymentMethod;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long orderId,
        String orderCode,
        Long reservationId,
        String guestName,
        Long tableId,
        String tableNumber,
        String tableName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String promotionCode,
        String promotionName,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String paymentReference,
        String vnpTxnRef,
        String vnpTransactionNo,
        String vnpBankCode,
        String vnpCardType,
        LocalDateTime issuedAt,
        LocalDateTime paymentStartedAt,
        LocalDateTime paidAt,
        List<InvoiceItemResponse> items) {
}
