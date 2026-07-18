package com.swp391.api.modules.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.SepayWebhookRequest;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import com.swp391.api.modules.payment.service.PaymentService;
import com.swp391.api.modules.payment.service.SepayProperties;
import com.swp391.api.modules.promotion.entity.DiscountType;
import com.swp391.api.modules.promotion.entity.Promotion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SepayProperties sepayProperties;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            SepayProperties sepayProperties,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.sepayProperties = sepayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentResponse createSepayPayment(Long orderId) {
        RestaurantOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open orders can be paid");
        }
        requireAllItemsServed(order);
        BigDecimal total = calculateTotal(order);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total must be greater than 0");
        }

        Payment existingPaid = paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID)
                .orElse(null);
        if (existingPaid != null) {
            return toResponse(existingPaid);
        }

        paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> payment.setStatus(PaymentStatus.CANCELLED));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider("SEPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(total);
        payment.setPaymentCode(buildPaymentCode(order));
        payment.setQrImageUrl(buildQrImageUrl(payment));
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse createCashPayment(Long orderId) {
        RestaurantOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open orders can be paid");
        }
        BigDecimal total = calculateTotal(order);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total must be greater than 0");
        }

        Payment existingPaid = paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID)
                .orElse(null);
        if (existingPaid != null) {
            return toResponse(existingPaid);
        }

        paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> payment.setStatus(PaymentStatus.CANCELLED));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider("CASH");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(total);
        payment.setPaymentCode(buildCashPaymentCode(order));
        payment.setPaidAt(LocalDateTime.now());
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getLatestPayment(Long orderId) {
        return paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    @Override
    @Transactional
    public Map<String, Boolean> handleSepayWebhook(SepayWebhookRequest request) {
        String transactionId = firstNonBlank(request.getId(), request.getReferenceCode(), request.getCode());
        if (transactionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transaction id");
        }
        if (paymentRepository.existsByProviderTransactionId(transactionId)) {
            return Map.of("success", true);
        }
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            return Map.of("success", true);
        }

        Payment payment = findPaymentFromWebhook(request);
        if (payment == null) {
            return Map.of("success", true);
        }
        if (request.getTransferAmount() == null || request.getTransferAmount().compareTo(payment.getAmount()) < 0) {
            return Map.of("success", true);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setProviderTransactionId(transactionId);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRawPayload(toJson(request));
        paymentRepository.save(payment);
        return Map.of("success", true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOrderPaid(Long orderId) {
        return paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Payment latestPaymentOrNull(Long orderId) {
        return paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId).orElse(null);
    }

    @Override
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderCode(),
                payment.getProvider(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getPaymentCode(),
                payment.getQrImageUrl(),
                payment.getCheckoutUrl(),
                payment.getProviderTransactionId(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private Payment findPaymentFromWebhook(SepayWebhookRequest request) {
        String searchable = (safe(request.getContent()) + " " + safe(request.getDescription()) + " " + safe(request.getCode()))
                .toUpperCase(Locale.ROOT);
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .filter(payment -> searchable.contains(payment.getPaymentCode().toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private String buildPaymentCode(RestaurantOrder order) {
        String prefix = sepayProperties.getTransferPrefix() == null || sepayProperties.getTransferPrefix().isBlank()
                ? "GS"
                : sepayProperties.getTransferPrefix().trim().toUpperCase(Locale.ROOT);
        return prefix + "ORD" + order.getId() + "PAY" + System.currentTimeMillis();
    }

    private String buildCashPaymentCode(RestaurantOrder order) {
        return "CASHORD" + order.getId() + "PAY" + System.currentTimeMillis();
    }

    private String buildQrImageUrl(Payment payment) {
        if (isBlank(sepayProperties.getBankCode()) || isBlank(sepayProperties.getAccountNumber())) {
            return null;
        }
        String accountName = URLEncoder.encode(safe(sepayProperties.getAccountName()), StandardCharsets.UTF_8);
        String addInfo = URLEncoder.encode(payment.getPaymentCode(), StandardCharsets.UTF_8);
        long amount = payment.getAmount().setScale(0, RoundingMode.HALF_UP).longValue();
        return "https://img.vietqr.io/image/"
                + sepayProperties.getBankCode().trim()
                + "-"
                + sepayProperties.getAccountNumber().trim()
                + "-"
                + sepayProperties.getVietqrTemplate()
                + ".png?amount="
                + amount
                + "&addInfo="
                + addInfo
                + "&accountName="
                + accountName;
    }

    private BigDecimal calculateTotal(RestaurantOrder order) {
        BigDecimal subtotal = order.getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(order.getPromotion(), subtotal);
        return subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireAllItemsServed(RestaurantOrder order) {
        boolean hasServedItem = order.getItems().stream()
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED
                        && item.getStatus() != OrderItemStatus.CANCELLED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "All non-cancelled items must be served before creating a payment");
        }
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        if (promotion == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENT
                ? subtotal.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        return discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String toJson(SepayWebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> !isBlank(value)).findFirst().orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
