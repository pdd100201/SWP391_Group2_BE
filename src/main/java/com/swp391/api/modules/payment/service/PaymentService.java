package com.swp391.api.modules.payment.service;

import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.payment.config.VnpayProperties;
import com.swp391.api.modules.payment.dto.InvoiceItemResponse;
import com.swp391.api.modules.payment.dto.InvoiceResponse;
import com.swp391.api.modules.payment.dto.PaymentRequest;
import com.swp391.api.modules.payment.dto.PaymentResponse;
import com.swp391.api.modules.payment.dto.VnpayIpnResponse;
import com.swp391.api.modules.payment.dto.VnpayReturnResult;
import com.swp391.api.modules.payment.entity.Invoice;
import com.swp391.api.modules.payment.entity.PaymentMethod;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.InvoiceRepository;
import com.swp391.api.modules.promotion.service.PromotionService;
import com.swp391.api.modules.promotion.service.PromotionService.PromotionApplication;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PaymentService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;
    private final PromotionService promotionService;
    private final VnpayProperties vnpayProperties;

    public PaymentService(
            InvoiceRepository invoiceRepository,
            OrderRepository orderRepository,
            TableRepository tableRepository,
            PromotionService promotionService,
            VnpayProperties vnpayProperties) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.promotionService = promotionService;
        this.vnpayProperties = vnpayProperties;
    }

    public InvoiceResponse getOrCreateInvoiceByOrder(Long orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .orElseGet(() -> createInvoice(findOrder(orderId)));
        syncInvoice(invoice);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long invoiceId) {
        return toResponse(findInvoice(invoiceId));
    }

    public InvoiceResponse applyPromotion(Long invoiceId, String code) {
        Invoice invoice = findInvoiceForUpdate(invoiceId);
        requireEditable(invoice);
        syncInvoice(invoice);
        PromotionApplication application = promotionService.validateForInvoice(code, invoice.getSubtotal());
        invoice.setPromotion(application.promotion());
        invoice.setPromotionCode(application.promotion().getCode());
        invoice.setPromotionName(application.promotion().getName());
        invoice.setDiscountAmount(application.discountAmount());
        invoice.setTotalAmount(invoice.getSubtotal().subtract(invoice.getDiscountAmount()).max(BigDecimal.ZERO).setScale(2));
        return toResponse(invoiceRepository.save(invoice));
    }

    public InvoiceResponse removePromotion(Long invoiceId) {
        Invoice invoice = findInvoiceForUpdate(invoiceId);
        requireEditable(invoice);
        invoice.setPromotion(null);
        invoice.setPromotionCode(null);
        invoice.setPromotionName(null);
        syncInvoice(invoice);
        return toResponse(invoiceRepository.save(invoice));
    }

    public PaymentResponse processPayment(Long invoiceId, PaymentRequest request, HttpServletRequest httpRequest) {
        Invoice invoice = findInvoiceForUpdate(invoiceId);
        syncInvoice(invoice);
        ensurePayable(invoice);

        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            return new PaymentResponse(toResponse(invoice), null, "Invoice is already paid");
        }
        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            String paymentUrl = startVnpayPayment(invoice, request.getBankCode(), httpRequest);
            return new PaymentResponse(toResponse(invoiceRepository.save(invoice)), paymentUrl, "Redirect customer to VNPAY");
        }

        markPaid(invoice, request.getPaymentMethod(), manualReference(invoice, request.getPaymentMethod()), null);
        return new PaymentResponse(toResponse(invoiceRepository.save(invoice)), null, "Payment completed");
    }

    public VnpayIpnResponse handleVnpayIpn(Map<String, String> params) {
        try {
            if (!validateSignature(params)) {
                return new VnpayIpnResponse("97", "Invalid signature");
            }
            String txnRef = params.get("vnp_TxnRef");
            Invoice invoice = invoiceRepository.findByVnpTxnRefForUpdate(txnRef).orElse(null);
            if (invoice == null) {
                return new VnpayIpnResponse("01", "Order not found");
            }
            if (!amountMatches(invoice, params.get("vnp_Amount"))) {
                return new VnpayIpnResponse("04", "invalid amount");
            }
            if (invoice.getPaymentStatus() == PaymentStatus.PAID || invoice.getPaymentStatus() == PaymentStatus.FAILED) {
                return new VnpayIpnResponse("02", "Order already confirmed");
            }
            applyVnpayResult(invoice, params);
            invoiceRepository.save(invoice);
            return new VnpayIpnResponse("00", "Confirm Success");
        } catch (Exception e) {
            return new VnpayIpnResponse("99", "Unknown error");
        }
    }

    public VnpayReturnResult handleVnpayReturn(Map<String, String> params) {
        if (!validateSignature(params)) {
            return new VnpayReturnResult(null, null, false, "Invalid VNPAY signature");
        }
        String txnRef = params.get("vnp_TxnRef");
        Invoice invoice = invoiceRepository.findByVnpTxnRefForUpdate(txnRef).orElse(null);
        if (invoice == null) {
            return new VnpayReturnResult(null, null, false, "Invoice not found");
        }
        if (!amountMatches(invoice, params.get("vnp_Amount"))) {
            return new VnpayReturnResult(invoice.getId(), invoice.getOrder().getId(), false, "Invalid amount returned from VNPAY");
        }
        if (invoice.getPaymentStatus() != PaymentStatus.PAID && invoice.getPaymentStatus() != PaymentStatus.FAILED) {
            applyVnpayResult(invoice, params);
            invoiceRepository.save(invoice);
        }
        boolean success = invoice.getPaymentStatus() == PaymentStatus.PAID;
        return new VnpayReturnResult(
                invoice.getId(),
                invoice.getOrder().getId(),
                success,
                success ? "VNPAY payment completed" : "VNPAY payment was not successful");
    }

    private Invoice createInvoice(RestaurantOrder order) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-" + LocalDate.now(VIETNAM_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setOrder(order);
        invoice.setPaymentStatus(PaymentStatus.DRAFT);
        invoice.setIssuedAt(LocalDateTime.now(VIETNAM_ZONE));
        syncInvoice(invoice);
        return invoiceRepository.save(invoice);
    }

    private void syncInvoice(Invoice invoice) {
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) return;
        BigDecimal subtotal = invoice.getOrder().getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        invoice.setSubtotal(subtotal);

        if (invoice.getPromotion() == null) {
            invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2));
            invoice.setTotalAmount(subtotal);
            return;
        }

        PromotionApplication application = promotionService.validateForInvoice(invoice.getPromotionCode(), subtotal);
        invoice.setPromotion(application.promotion());
        invoice.setPromotionCode(application.promotion().getCode());
        invoice.setPromotionName(application.promotion().getName());
        invoice.setDiscountAmount(application.discountAmount());
        invoice.setTotalAmount(subtotal.subtract(application.discountAmount()).max(BigDecimal.ZERO).setScale(2));
    }

    private void ensurePayable(Invoice invoice) {
        RestaurantOrder order = invoice.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled orders cannot be paid");
        }
        if (invoice.getPaymentStatus() == PaymentStatus.PENDING && invoice.getPaymentMethod() == PaymentMethod.VNPAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice is waiting for VNPAY confirmation");
        }
        if (invoice.getSubtotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has no payable items");
        }
        if (order.getStatus() == OrderStatus.OPEN) {
            boolean hasServedItem = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
            boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                    item.getStatus() != OrderItemStatus.SERVED && item.getStatus() != OrderItemStatus.CANCELLED);
            if (!hasServedItem || hasUnfinishedItem) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before payment");
            }
        }
    }

    private String startVnpayPayment(Invoice invoice, String bankCode, HttpServletRequest request) {
        requireVnpayConfig();
        if (invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VNPAY requires a payment amount greater than zero");
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        String txnRef = "INV" + invoice.getId() + now.format(VNP_DATE_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        invoice.setPaymentStatus(PaymentStatus.PENDING);
        invoice.setPaymentMethod(PaymentMethod.VNPAY);
        invoice.setPaymentStartedAt(now);
        invoice.setVnpTxnRef(txnRef);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayProperties.getTmnCode());
        params.put("vnp_Amount", toVnpAmount(invoice.getTotalAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan hoa don " + invoice.getInvoiceNumber());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnpayProperties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp(request));
        params.put("vnp_CreateDate", now.format(VNP_DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE_FORMAT));
        if (bankCode != null && !bankCode.isBlank()) {
            params.put("vnp_BankCode", bankCode.trim());
        }

        String query = buildQuery(params);
        String secureHash = hmacSha512(buildHashData(params), vnpayProperties.getHashSecret());
        return vnpayProperties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    private void applyVnpayResult(Invoice invoice, Map<String, String> params) {
        copyVnpayFields(invoice, params);
        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        if (success) {
            markPaid(invoice, PaymentMethod.VNPAY, params.get("vnp_TransactionNo"), params);
        } else {
            invoice.setPaymentStatus(PaymentStatus.FAILED);
            invoice.setPaymentMethod(PaymentMethod.VNPAY);
        }
    }

    private void markPaid(Invoice invoice, PaymentMethod method, String reference, Map<String, String> vnpayParams) {
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) return;
        invoice.setPaymentStatus(PaymentStatus.PAID);
        invoice.setPaymentMethod(method);
        invoice.setPaymentReference(reference);
        invoice.setPaidAt(LocalDateTime.now(VIETNAM_ZONE));
        if (vnpayParams != null) {
            copyVnpayFields(invoice, vnpayParams);
        }
        if (invoice.getPromotion() != null) {
            promotionService.increaseUsedCount(invoice.getPromotion());
        }
        closeOrderAfterPayment(invoice.getOrder());
    }

    private void closeOrderAfterPayment(RestaurantOrder order) {
        if (order.getStatus() == OrderStatus.OPEN) {
            order.setStatus(OrderStatus.CLOSED);
            order.setClosedAt(LocalDateTime.now(VIETNAM_ZONE));
        }
        Reservation reservation = order.getReservation();
        reservation.setStatus(ReservationStatus.COMPLETED);
        updateAssignedTableStatus(reservation, RestaurantTable.TableStatus.CLEANING);
        orderRepository.save(order);
    }

    private void updateAssignedTableStatus(Reservation reservation, RestaurantTable.TableStatus status) {
        Long tableId = resolvePrimaryTableId(reservation);
        if (tableId == null) return;
        tableRepository.findByIdForUpdate(tableId).ifPresent(table -> {
            if (Boolean.TRUE.equals(table.getIsActive())) {
                table.setStatus(status);
            }
        });
    }

    private Long resolvePrimaryTableId(Reservation reservation) {
        if (reservation.getTableId() != null) {
            return reservation.getTableId();
        }
        List<RestaurantTable> tables = reservation.getTables();
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        return tables.get(0).getId();
    }

    private void copyVnpayFields(Invoice invoice, Map<String, String> params) {
        invoice.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        invoice.setVnpResponseCode(params.get("vnp_ResponseCode"));
        invoice.setVnpTransactionStatus(params.get("vnp_TransactionStatus"));
        invoice.setVnpBankCode(params.get("vnp_BankCode"));
        invoice.setVnpCardType(params.get("vnp_CardType"));
    }

    private boolean amountMatches(Invoice invoice, String vnpAmount) {
        return toVnpAmount(invoice.getTotalAmount()).equals(vnpAmount);
    }

    private String toVnpAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .toPlainString();
    }

    private String manualReference(Invoice invoice, PaymentMethod method) {
        return method.name() + "-" + invoice.getInvoiceNumber() + "-"
                + LocalDateTime.now(VIETNAM_ZONE).format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        RestaurantOrder order = invoice.getOrder();
        Reservation reservation = order.getReservation();
        RestaurantTable table = findAssignedTable(reservation);
        List<InvoiceItemResponse> items = order.getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
                .sorted(Comparator.comparing(OrderItem::getId))
                .map(item -> new InvoiceItemResponse(
                        item.getId(),
                        item.getMenuItemName(),
                        item.getCategoryName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal(),
                        item.getNote(),
                        item.getStatus()))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                order.getId(),
                order.getOrderCode(),
                reservation.getReservationId(),
                reservation.getFullName(),
                reservation.getTableId(),
                table == null ? null : table.getTableNumber(),
                table == null ? null : table.getTableName(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getPromotionCode(),
                invoice.getPromotionName(),
                invoice.getPaymentStatus(),
                invoice.getPaymentMethod(),
                invoice.getPaymentReference(),
                invoice.getVnpTxnRef(),
                invoice.getVnpTransactionNo(),
                invoice.getVnpBankCode(),
                invoice.getVnpCardType(),
                invoice.getIssuedAt(),
                invoice.getPaymentStartedAt(),
                invoice.getPaidAt(),
                items);
    }

    private RestaurantTable findAssignedTable(Reservation reservation) {
        Long tableId = resolvePrimaryTableId(reservation);
        if (tableId == null) return null;
        return tableRepository.findById(tableId).orElse(null);
    }

    private RestaurantOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private Invoice findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private Invoice findInvoiceForUpdate(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private void requireEditable(Invoice invoice) {
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid invoices cannot be changed");
        }
        if (invoice.getPaymentStatus() == PaymentStatus.PENDING && invoice.getPaymentMethod() == PaymentMethod.VNPAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice is waiting for VNPAY confirmation");
        }
    }

    private void requireVnpayConfig() {
        if (isBlank(vnpayProperties.getTmnCode())
                || isBlank(vnpayProperties.getHashSecret())
                || isBlank(vnpayProperties.getPayUrl())
                || isBlank(vnpayProperties.getReturnUrl())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VNPAY configuration is incomplete");
        }
    }

    private boolean validateSignature(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        if (secureHash == null || secureHash.isBlank()) return false;
        Map<String, String> signedParams = params.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        String signed = hmacSha512(buildHashData(signedParams), vnpayProperties.getHashSecret());
        return secureHash.equalsIgnoreCase(signed);
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to sign VNPAY request");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
