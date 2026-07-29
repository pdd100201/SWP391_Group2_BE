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
import com.swp391.api.modules.payment.entity.Bill;
import com.swp391.api.modules.payment.entity.BillStatus;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.BillRepository;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import com.swp391.api.modules.payment.service.PaymentService;
import com.swp391.api.modules.payment.service.SepayProperties;
import com.swp391.api.modules.promotion.entity.DiscountType;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.entity.PromotionStatus;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import com.swp391.api.modules.reservation.entity.Reservation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentServiceImpl implements PaymentService {
    // Repository dung de doc/ghi payment, bill, order va promotion trong database.
    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final OrderRepository orderRepository;
    private final PromotionRepository promotionRepository;
    // Cau hinh SePay/VietQR: ngan hang, so tai khoan, ten tai khoan, prefix chuyen khoan.
    private final SepayProperties sepayProperties;
    // Dung de convert webhook request thanh JSON de luu/debug.
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BillRepository billRepository,
            OrderRepository orderRepository,
            PromotionRepository promotionRepository,
            SepayProperties sepayProperties,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
        this.promotionRepository = promotionRepository;
        this.sepayProperties = sepayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    // Tao payment SePay cho 1 order rieng le: tao payment PENDING, paymentCode va QR.
    public PaymentResponse createSepayPayment(Long orderId) {
        //Tìm order và khóa nó lại để tránh 2 người thanh toán cùng lúc.
        RestaurantOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        //Chỉ order đang mở mới được thanh toán.
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open orders can be paid");
        }
        //Tất cả món phải được phục vụ xong mới cho thanh toán.
        requireAllItemsServed(order);
        BigDecimal total = calculateTotal(order);
        //Nếu tổng tiền <= 0 thì không tạo payment.
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total must be greater than 0");
        }

        Payment existingPaid = paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID)
                .orElse(null);
        // Neu order da thanh toan roi thi tra ve payment cu, khong tao them payment moi.
        if (existingPaid != null) {
            return toResponse(existingPaid);
        }

        // Neu co payment PENDING cu thi huy truoc khi tao payment moi.
        paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> payment.setStatus(PaymentStatus.CANCELLED));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setBill(prepareBill(order, total, BillStatus.PENDING, null));
        payment.setProvider("SEPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(total);
        payment.setPaymentCode(buildPaymentCode(order));
        payment.setQrImageUrl(buildQrImageUrl(payment));
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    // Tao payment tien mat cho 1 order rieng le. Cash duoc set PAID ngay.
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
        // Neu da co payment PAID thi khong tao trung.
        if (existingPaid != null) {
            return toResponse(existingPaid);
        }

        // Huy payment PENDING cu neu nhan vien doi sang thanh toan cash.
        paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> payment.setStatus(PaymentStatus.CANCELLED));

        Payment payment = new Payment();
        payment.setOrder(order);
        LocalDateTime paidAt = LocalDateTime.now();
        payment.setBill(prepareBill(order, total, BillStatus.PAID, paidAt));
        payment.setProvider("CASH");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(total);
        payment.setPaymentCode(buildCashPaymentCode(order));
        payment.setPaidAt(paidAt);
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    // Tao SePay QR cho bill chung cua reservation. Day la luong chinh cua man Payment hien tai.
    // FE bam "Create SePay QR" -> POST /payments/bills/reservations/{id}/sepay -> ham nay.
    public void createReservationSepayPayment(Long reservationId) {
        // Buoc 1: lay tat ca order cua reservation. Mot reservation co the co nhieu order/table.
        List<RestaurantOrder> orders = findReservationOrders(reservationId);
        //Kiểm tra tất cả món phải SERVED rồi mới cho thanh toán.
        requireAllReservationItemsServed(orders);
        //Tạo hoặc lấy bill, tính lại tiền, rồi set bill status là PENDING.
        // Buoc 3: tao/lay bill va set status PENDING vi dang cho khach chuyen khoan.
        Bill bill = prepareReservationBill(orders, BillStatus.PENDING, null);
        //Bill phải có tổng tiền > 0
        if (bill.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill total must be greater than 0");
        }
        //Nếu bill đã có payment PAID thì không cho tạo payment nữa.
        // Buoc 5: neu bill da co payment PAID thi khong tao QR moi nua.
        paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(bill.getId(), PaymentStatus.PAID)
                .ifPresent(payment -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill is already paid");
                });
        //Nếu trước đó có payment pending cũ thì hủy nó
        cancelPendingBillPayment(bill);

        // Buoc 7: tao Payment SEPAY moi, ban dau la PENDING.
        Payment payment = new Payment();
        // Payment van gan 1 order dai dien, nhung amount lay theo bill chung cua reservation.
        payment.setOrder(orders.get(0));
        payment.setBill(bill);
        payment.setProvider("SEPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(bill.getTotal());
        //Tạo nội dung chuyển khoản.
        // paymentCode la noi dung chuyen khoan, webhook se dung code nay de tim payment.
        payment.setPaymentCode(buildPaymentCode(orders.get(0)));
        //Tạo link ảnh QR.
        // Tao link anh VietQR de FE hien QR cho khach quet.
        payment.setQrImageUrl(buildQrImageUrl(payment));
        // Luu payment. Luc nay khach chua chuyen khoan nen payment van PENDING.
        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    // Xac nhan thanh toan tien mat cho bill chung cua reservation.
    // FE bam Confirm Cash trong modal -> POST /payments/bills/reservations/{id}/cash -> ham nay.
    public void createReservationCashPayment(Long reservationId) {
        // Cash cung thanh toan bill chung cua reservation, nen phai lay order group truoc.
        List<RestaurantOrder> orders = findReservationOrders(reservationId);
        requireAllReservationItemsServed(orders);
        LocalDateTime paidAt = LocalDateTime.now();
        // Khac SePay: cash la nhan vien da nhan tien, nen bill set PAID ngay.
        Bill bill = prepareReservationBill(orders, BillStatus.PAID, paidAt);
        if (bill.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill total must be greater than 0");
        }
        if (paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(bill.getId(), PaymentStatus.PAID).isPresent()) {
            // Neu bill da PAID thi ket thuc, tranh tao them payment cash.
            return;
        }
        cancelPendingBillPayment(bill);

        // Tao payment CASH moi, status PAID ngay, khong can QR/webhook.
        Payment payment = new Payment();
        payment.setOrder(orders.get(0));
        payment.setBill(bill);
        payment.setProvider("CASH");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(bill.getTotal());
        payment.setPaymentCode(buildCashPaymentCode(orders.get(0)));
        payment.setPaidAt(paidAt);
        // Luu payment tien mat vao database.
        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    // FE bam Cancel pending payment khi SePay QR dang cho thanh toan -> ham nay.
    public void cancelReservationPayment(Long reservationId) {
        // Tim bill theo reservation.
        Bill bill = billRepository.findByReservation_ReservationId(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        // Chi huy duoc payment PENDING; neu khong co pending thi bao loi.
        Payment pending = paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(bill.getId(), PaymentStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pending payment to cancel"));
        // Huy payment va mo khoa bill ve DRAFT.
        pending.setStatus(PaymentStatus.CANCELLED);
        bill.setStatus(BillStatus.DRAFT);
        bill.setLockedAt(null);
        bill.setPaidAt(null);
    }

    @Override
    @Transactional
    //áp mã giảm giá vào bill của một reservation
    public void applyReservationPromotion(Long reservationId, String code) {
        // Lay order group de co du lieu tinh subtotal cua bill.
        List<RestaurantOrder> orders = findReservationOrders(reservationId);
        // Tao/lay bill hien tai va tinh lai tien truoc khi apply ma.
        Bill bill = getOrCreateReservationBill(orders);
        // Bill dang PENDING/PAID thi khong cho sua promotion.
        requireDraftBill(bill);
        if (bill.getSubtotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add order items before applying a promotion");
        }

        // Tim promotion theo code, khong phan biet hoa thuong.
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion code not found"));
        // Kiem tra promotion active, con han, bill du minimum, con usage limit.
        validatePromotion(promotion, bill.getSubtotal(), bill.getPromotion());

        Promotion currentPromotion = bill.getPromotion();
        //Nếu bill đang có mã cũ khác mã mới, giảm lượt dùng mã cũ.
        if (currentPromotion != null && !currentPromotion.getId().equals(promotion.getId())) {
            decrementUsedCount(currentPromotion);
        }
        //Nếu bill chưa có mã hoặc đổi sang mã mới, tăng lượt dùng mã mới.
        if (currentPromotion == null || !currentPromotion.getId().equals(promotion.getId())) {
            promotion.setUsedCount((promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) + 1);
        }
        bill.setPromotion(promotion);
        // Gan promotion xong phai tinh lai subtotal, discount va total cua bill.
        refreshBillTotals(bill, orders);
    }

    @Override
    @Transactional
    // Go promotion khoi bill reservation va tinh lai total ve khong co discount.
    public void removeReservationPromotion(Long reservationId) {
        List<RestaurantOrder> orders = findReservationOrders(reservationId);
        Bill bill = getOrCreateReservationBill(orders);
        requireDraftBill(bill);
        if (bill.getPromotion() != null) {
            decrementUsedCount(bill.getPromotion());
        }
        bill.setPromotion(null);
        refreshBillTotals(bill, orders);
    }

    @Override
    @Transactional
    public void syncOpenReservationBill(Long reservationId) {
        Bill bill = billRepository.findByReservation_ReservationId(reservationId).orElse(null);
        if (bill == null || bill.getStatus() == BillStatus.PAID) {
            return;
        }
        List<RestaurantOrder> orders = findReservationOrdersOrEmpty(reservationId);
        if (orders.isEmpty()) {
            return;
        }

        BigDecimal previousTotal = bill.getTotal() == null ? BigDecimal.ZERO : bill.getTotal();
        refreshBillTotals(bill, orders);

        if (bill.getStatus() == BillStatus.PENDING && previousTotal.compareTo(bill.getTotal()) != 0) {
            cancelPendingBillPayment(bill);
            bill.setStatus(BillStatus.DRAFT);
            bill.setLockedAt(null);
            bill.setPaidAt(null);
        }
        billRepository.save(bill);
    }

    @Override
    @Transactional
    public void syncReservationBillAfterItemVoid(Long reservationId) {
        Bill bill = billRepository.findByReservation_ReservationId(reservationId).orElse(null);
        if (bill == null) {
            return;
        }
        List<RestaurantOrder> orders = findReservationOrdersOrEmpty(reservationId);
        if (orders.isEmpty()) {
            return;
        }

        BigDecimal previousTotal = bill.getTotal() == null ? BigDecimal.ZERO : bill.getTotal();
        refreshBillTotals(bill, orders);

        if (bill.getStatus() == BillStatus.PENDING && previousTotal.compareTo(bill.getTotal()) != 0) {
            cancelPendingBillPayment(bill);
            bill.setStatus(BillStatus.DRAFT);
            bill.setLockedAt(null);
            bill.setPaidAt(null);
        } else if (bill.getStatus() == BillStatus.PAID) {
            paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(bill.getId(), PaymentStatus.PAID)
                    .ifPresent(payment -> payment.setAmount(bill.getTotal()));
        }
        billRepository.save(bill);
    }

    @Override
    @Transactional(readOnly = true)
    // Lay payment moi nhat cua order.
    public PaymentResponse getLatestPayment(Long orderId) {
        return paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    @Override
    @Transactional
    //1. Lấy transactionId từ webhook
    //2. Nếu transaction đã xử lý rồi thì bỏ qua
    //3. Chỉ xử lý tiền vào
    //4. Tìm payment pending theo paymentCode trong nội dung chuyển khoản
    //5. Kiểm tra số tiền chuyển có đủ không
    //6. Nếu đúng thì set payment PAID và bill PAID
    // Webhook la API SePay goi ve sau khi co giao dich ngan hang.
    public Map<String, Boolean> handleSepayWebhook(SepayWebhookRequest request) {
        // SePay co the gui id giao dich o nhieu field khac nhau, nen lay field dau tien co gia tri.
        String transactionId = firstNonBlank(request.getId(), request.getReferenceCode(), request.getCode());
        if (transactionId == null) {
            // Khong co ma giao dich thi request webhook khong hop le.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transaction id");
        }
        if (paymentRepository.existsByProviderTransactionId(transactionId)) {
            // Transaction da xu ly roi thi tra success de SePay khong retry.
            return Map.of("success", true);
        }
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            // Chi xu ly tien vao. Tien ra thi bo qua.
            return Map.of("success", true);
        }
        //tìm payment
        // Tim payment PENDING dua tren paymentCode nam trong noi dung chuyen khoan.
        Payment payment = findPaymentFromWebhook(request);
        if (payment == null) {
            // Khong tim thay payment pending phu hop thi bo qua.
            return Map.of("success", true);
        }
        if (request.getTransferAmount() == null || request.getTransferAmount().compareTo(payment.getAmount()) < 0) {
            // Tien chuyen nho hon bill total thi chua danh dau PAID.
            return Map.of("success", true);
        }

        // Giao dich hop le: payment va bill cung chuyen sang PAID.
        payment.setStatus(PaymentStatus.PAID);
        // Luu ma giao dich cua SePay de lan sau gap lai transaction nay thi khong xu ly trung.
        payment.setProviderTransactionId(transactionId);
        // Ghi thoi diem he thong nhan webhook hop le.
        payment.setPaidAt(LocalDateTime.now());
        // Bill gan voi payment cung duoc danh dau PAID de FE hien da thanh toan.
        payment.getBill().setStatus(BillStatus.PAID);
        payment.getBill().setPaidAt(payment.getPaidAt());
        // Luu payload goc de debug/doi soat khi can.
        payment.setRawPayload(toJson(request));
        paymentRepository.save(payment);
        return Map.of("success", true);
    }

    @Override
    @Transactional(readOnly = true)
    // Kiem tra order da co payment PAID chua.
    public boolean isOrderPaid(Long orderId) {
        return paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    // Lay payment moi nhat neu co, neu khong thi tra null.
    public Payment latestPaymentOrNull(Long orderId) {
        return paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId).orElse(null);
    }

    @Override
    // Doi Payment entity sang PaymentResponse de tra ve frontend.
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
        // Gop content/description/code tu webhook de tim paymentCode trong noi dung chuyen khoan.
        // Khach co the chuyen khoan voi noi dung dai hon, chi can noi dung co chua paymentCode la match.
        String searchable = (safe(request.getContent()) + " " + safe(request.getDescription()) + " " + safe(request.getCode()))
                .toUpperCase(Locale.ROOT);
        return paymentRepository.findAll().stream()
                //Tìm payment đang PENDING mà nội dung chuyển khoản có chứa paymentCode.
                // Chi tim payment PENDING, vi PAID/CANCELLED khong can xu ly webhook thanh toan nua.
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                // paymentCode tao luc tao QR phai nam trong noi dung chuyen khoan tu SePay.
                .filter(payment -> searchable.contains(payment.getPaymentCode().toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private List<RestaurantOrder> findReservationOrders(Long reservationId) {
        // Lay cac order thuoc reservation, bo qua order da CANCELLED.
        List<RestaurantOrder> orders = orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(reservationId)
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order group not found");
        }
        return orders;
    }

    private List<RestaurantOrder> findReservationOrdersOrEmpty(Long reservationId) {
        return orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(reservationId)
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();
    }

    private Bill getOrCreateReservationBill(List<RestaurantOrder> orders) {
        // Lay bill cua reservation neu co, neu chua co thi tao bill moi.
        RestaurantOrder firstOrder = orders.get(0);
        Reservation reservation = firstOrder.getReservation();
        Bill bill = billRepository.findByReservation_ReservationId(reservation.getReservationId()).orElseGet(() -> {
            Bill newBill = new Bill();
            newBill.setBillCode("BILL-" + reservation.getReservationId() + "-" + System.currentTimeMillis());
            newBill.setReservation(reservation);
            return newBill;
        });
        refreshBillTotals(bill, orders);
        // Luu bill sau khi da tinh lai tong tien.
        return billRepository.save(bill);
    }

    private Bill prepareReservationBill(List<RestaurantOrder> orders, BillStatus status, LocalDateTime paidAt) {
        // Chuan bi bill truoc khi tao payment: tinh tien, set status, lock bill va paidAt neu co.
        Bill bill = getOrCreateReservationBill(orders);
        bill.setStatus(status);
        bill.setLockedAt(LocalDateTime.now());
        bill.setPaidAt(paidAt);
        return billRepository.save(bill);
    }
    //tính lại toàn bộ tiền của bill.
    private void refreshBillTotals(Bill bill, List<RestaurantOrder> orders) {
        //tiền tất cả món trong tất cả order bỏ qua món đã cancelled
        BigDecimal subtotal = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> isBillableStatus(item.getStatus()))
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(bill.getPromotion(), subtotal);
        //total = subtotal - discount
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(discount);
        bill.setTotal(subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private void requireDraftBill(Bill bill) {
        // Bill da PENDING/PAID thi khong cho sua promotion nua.
        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill is locked by a pending or paid payment");
        }
    }

    private void requireAllReservationItemsServed(List<RestaurantOrder> orders) {
        // Reservation chi duoc thanh toan khi co mon SERVED va khong con mon nao dang lam.
        boolean hasServedItem = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getStatus() != OrderItemStatus.SERVED
                        && item.getStatus() != OrderItemStatus.CANCELLED
                        && item.getStatus() != OrderItemStatus.VOIDED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "All non-cancelled items must be served before creating a payment");
        }
    }

    private void cancelPendingBillPayment(Bill bill) {
        // Moi bill chi nen co 1 payment PENDING tai mot thoi diem.
        paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(bill.getId(), PaymentStatus.PENDING)
                .ifPresent(payment -> payment.setStatus(PaymentStatus.CANCELLED));
    }
    //Tạo paymentCode
    private String buildPaymentCode(RestaurantOrder order) {
        // Prefix lay tu cau hinh SePay; neu khong co thi mac dinh la GS.
        String prefix = sepayProperties.getTransferPrefix() == null || sepayProperties.getTransferPrefix().isBlank()
                ? "GS"
                : sepayProperties.getTransferPrefix().trim().toUpperCase(Locale.ROOT);
        // Format code: PREFIX + ORD + orderId + PAY + timestamp de moi payment co noi dung chuyen khoan rieng.
        return prefix + "ORD" + order.getId() + "PAY" + System.currentTimeMillis();
    }

    private String buildCashPaymentCode(RestaurantOrder order) {
        // Ma cash dung de trace trong he thong, khong dung lam noi dung chuyen khoan.
        return "CASHORD" + order.getId() + "PAY" + System.currentTimeMillis();
    }

    private Bill prepareBill(RestaurantOrder order, BigDecimal total, BillStatus status, LocalDateTime paidAt) {
        // Chuan bi bill cho luong payment theo 1 order rieng le.
        Long reservationId = order.getReservation().getReservationId();
        Bill bill = billRepository.findByReservation_ReservationId(reservationId).orElseGet(() -> {
            Bill newBill = new Bill();
            newBill.setBillCode("BILL-" + reservationId + "-" + System.currentTimeMillis());
            newBill.setReservation(order.getReservation());
            return newBill;
        });
        BigDecimal subtotal = order.getItems().stream()
                .filter(item -> isBillableStatus(item.getStatus()))
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        bill.setPromotion(order.getPromotion());
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(subtotal.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        bill.setTotal(total);
        bill.setStatus(status);
        bill.setLockedAt(LocalDateTime.now());
        bill.setPaidAt(paidAt);
        return billRepository.save(bill);
    }
    //Tạo QR Code
    private String buildQrImageUrl(Payment payment) {
        // Thieu bankCode/accountNumber thi khong tao duoc link QR.
        if (isBlank(sepayProperties.getBankCode()) || isBlank(sepayProperties.getAccountNumber())) {
            return null;
        }
        // Encode thong tin de dua vao URL VietQR.
        String accountName = URLEncoder.encode(safe(sepayProperties.getAccountName()), StandardCharsets.UTF_8);
        // addInfo chinh la noi dung chuyen khoan, phai trung paymentCode de webhook tim lai duoc payment.
        String addInfo = URLEncoder.encode(payment.getPaymentCode(), StandardCharsets.UTF_8);
        // QR ngan hang thuong dung so tien lam tron VND, khong lay phan thap phan.
        long amount = payment.getAmount().setScale(0, RoundingMode.HALF_UP).longValue();
        // URL nay tra ve anh QR VietQR; FE chi can dat vao the img la hien QR.
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
    //tính số tiền cuối cùng của một order sau khi trừ promotion.
    private BigDecimal calculateTotal(RestaurantOrder order) {
        // Total cua order = tong mon chua CANCELLED - discount.
        BigDecimal subtotal = order.getItems().stream()
                .filter(item -> isBillableStatus(item.getStatus()))
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(order.getPromotion(), subtotal);
        return subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
    //Tất cả món phải được phục vụ xong mới cho thanh toán.
    private void requireAllItemsServed(RestaurantOrder order) {
        // Order chi duoc thanh toan khi co mon SERVED va khong con mon nao dang unfinished.
        boolean hasServedItem = order.getItems().stream()
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED
                        && item.getStatus() != OrderItemStatus.CANCELLED
                        && item.getStatus() != OrderItemStatus.VOIDED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "All non-cancelled items must be served before creating a payment");
        }
    }
    //công thức tính số tiền giảm.
    private boolean isBillableStatus(OrderItemStatus status) {
        return status != OrderItemStatus.CANCELLED && status != OrderItemStatus.VOIDED;
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        // Khong co promotion hoac bill <= 0 thi discount = 0.
        if (promotion == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        //Nếu PERCENT -> subtotal * value / 100, FIXED -> value
        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENT
                ? subtotal.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        //vượt max discount -> lấy max discount, vượt subtotal -> lấy subtotal
        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        return discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePromotion(Promotion promotion, BigDecimal subtotal, Promotion currentPromotion) {
        // Chi kiem tra promotion co duoc dung khong, chua tinh tien giam.
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            //phải active
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is inactive");
        }
        //Phải trong thời gian hiệu lực
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is outside its valid period");
        }
        //Bill phải đủ minimum
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill total does not meet the minimum amount");
        }
        //Không vượt usage limit
        boolean samePromotion = currentPromotion != null && currentPromotion.getId().equals(promotion.getId());
        if (!samePromotion && promotion.getUsageLimit() != null
                && (promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) >= promotion.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion usage limit has been reached");
        }
    }
    //giảm số lượt đã dùng khi bỏ mã hoặc đổi mã.
    private void decrementUsedCount(Promotion promotion) {
        int usedCount = promotion.getUsedCount() == null ? 0 : promotion.getUsedCount();
        promotion.setUsedCount(Math.max(0, usedCount - 1));
    }

    private String toJson(SepayWebhookRequest request) {
        // Luu webhook goc de sau nay doi soat/debug giao dich.
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        // Lay gia tri dau tien khong rong trong cac field id/reference/code.
        return Arrays.stream(values).filter(value -> !isBlank(value)).findFirst().orElse(null);
    }

    private boolean isBlank(String value) {
        // Helper kiem tra null hoac chuoi rong.
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        // Helper tranh null khi noi chuoi.
        return value == null ? "" : value;
    }
}
