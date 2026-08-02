package com.swp391.api.modules.order.service;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderGroupResponse;
import com.swp391.api.modules.order.dto.OrderItemResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.dto.VoidOrderItemRequest;
import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.dto.BillResponse;
import com.swp391.api.modules.payment.entity.Bill;
import com.swp391.api.modules.payment.repository.BillRepository;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import com.swp391.api.modules.qr.entity.QrOrder;
import com.swp391.api.modules.qr.entity.QrOrderItem;
import com.swp391.api.modules.qr.repository.QrOrderItemRepository;
import com.swp391.api.modules.qr.repository.QrOrderRepository;
import com.swp391.api.modules.payment.service.PaymentService;
import com.swp391.api.modules.promotion.entity.DiscountType;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.entity.PromotionStatus;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
// Điều phối toàn bộ nghiệp vụ Order. @Transactional ở cấp lớp giúp mỗi thao tác ghi
// thành công trọn vẹn hoặc rollback toàn bộ khi có lỗi giữa Order, Table, Bill và Payment.
public class OrderService {
    // Receptionist được xem/quản lý order nhưng không được chuyển tiến độ chế biến món.
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER");
    private static final Set<String> PAYMENT_ADJUSTMENT_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST");

    // Repository của dữ liệu lõi Order, Reservation, Table, Menu và User.
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final MenuService menuService;

    // Thành phần liên quan Promotion, hóa đơn và giao dịch thanh toán.
    private final PromotionRepository promotionRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;

    // Nguồn order QR cũ vẫn được đọc và chuyển về cùng một OrderResponse.
    private final QrOrderRepository qrOrderRepository;
    private final QrOrderItemRepository qrOrderItemRepository;
    private final PaymentService paymentService;

    // Constructor injection làm rõ toàn bộ dependency và giúp service dễ kiểm thử.
    public OrderService(
            OrderRepository orderRepository,
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            MenuItemRepository menuItemRepository,
            UserRepository userRepository,
            MenuService menuService,
            PromotionRepository promotionRepository,
            BillRepository billRepository,
            PaymentRepository paymentRepository,
            QrOrderRepository qrOrderRepository,
            QrOrderItemRepository qrOrderItemRepository,
            PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.menuService = menuService;
        this.promotionRepository = promotionRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.qrOrderRepository = qrOrderRepository;
        this.qrOrderItemRepository = qrOrderItemRepository;
        this.paymentService = paymentService;
    }

    // Luồng tạo một order cho bàn chính của reservation (API tương thích cũ).
    // Reservation và bàn được khóa để ngăn hai request đồng thời tạo trùng.
    public OrderResponse create(CreateOrderRequest request) {
        User waiter = currentUserRequired();
        // SELECT ... FOR UPDATE giữ reservation ổn định trong suốt transaction tạo order.
        Reservation reservation = reservationRepository.findByIdForUpdate(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ARRIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be checked in before opening an order");
        }
        List<RestaurantTable> assignedTables = findAssignedTables(reservation);
        if (assignedTables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be assigned to a table before opening an order");
        }
        Long assignedTableId = resolvePrimaryTableId(reservation);
        if (reservation.getTableId() == null) {
            // Đồng bộ trường tableId cũ với bàn đầu tiên trong quan hệ nhiều bàn.
            reservation.setTableId(assignedTableId);
        }
        if (orderRepository.findByReservationReservationIdAndTableId(
                reservation.getReservationId(), assignedTableId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This table already has an order");
        }
        RestaurantTable table = tableRepository.findByIdForUpdate(assignedTableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned table not found"));
        if (!Boolean.TRUE.equals(table.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned table is not active");
        }
        if (table.getStatus() != RestaurantTable.TableStatus.OCCUPIED) {
            // Mở order đồng nghĩa bàn bước vào trạng thái đang có khách.
            table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        }

        RestaurantOrder order = new RestaurantOrder();
        order.setOrderCode("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setReservation(reservation);
        order.setTableId(assignedTableId);
        order.setWaiter(waiter);
        // Token UUID bỏ dấu gạch ngang có 32 ký tự khó đoán và không chứa thông tin nhạy cảm.
        order.setPublicAccessToken(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.OPEN);
        order.setNote(normalize(request.getNote()));
        return toResponse(orderRepository.save(order));
    }

    // Luồng hiện tại của Order Management: tạo order còn thiếu cho mọi bàn đã gán.
    // Bàn đã có order được bỏ qua nên người dùng có thể bấm "sync" an toàn.
    public OrderGroupResponse createTableOrders(CreateOrderRequest request) {
        User waiter = currentUserRequired();
        Reservation reservation = reservationRepository.findByIdForUpdate(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ARRIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be checked in before opening an order");
        }
        List<RestaurantTable> assignedTables = findAssignedTables(reservation);
        if (assignedTables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be assigned to a table before opening an order");
        }
        if (reservation.getTableId() == null) {
            reservation.setTableId(assignedTables.get(0).getId());
        }

        // Mỗi bàn sinh một order riêng nhưng mọi order vẫn cùng reservation/bill.
        for (RestaurantTable assignedTable : assignedTables) {
            Long tableId = assignedTable.getId();
            if (orderRepository.findByReservationReservationIdAndTableId(
                    reservation.getReservationId(), tableId).isPresent()) {
                // Idempotent ở cấp bàn: sync lại không tạo bản ghi trùng.
                continue;
            }
            RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned table not found"));
            if (!Boolean.TRUE.equals(table.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned table is not active");
            }
            if (table.getStatus() != RestaurantTable.TableStatus.OCCUPIED) {
                table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
            }
            RestaurantOrder order = newOrder(reservation, tableId, waiter, request.getNote());
            orderRepository.save(order);
        }
        return getGroup(reservation.getReservationId());
    }

    // Khởi tạo thông tin chung của một RestaurantOrder mới và token QR riêng cho bàn.
    private RestaurantOrder newOrder(Reservation reservation, Long tableId, User waiter, String note) {
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderCode("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setReservation(reservation);
        order.setTableId(tableId);
        order.setWaiter(waiter);
        order.setPublicAccessToken(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.OPEN);
        order.setNote(normalize(note));
        return order;
    }

    // QR flow gọi hàm này khi bàn chưa có restaurant_order đang mở.
    public OrderResponse createForTable(Long tableId, Long waiterId) {
        Reservation reservation = reservationRepository.findActiveReservationByTableId(tableId)
                .or(() -> reservationRepository.findByTableIdAndStatus(tableId, ReservationStatus.ARRIVED))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No checked-in reservation found for table " + tableId));
        if (orderRepository.findByReservationReservationIdAndTableId(reservation.getReservationId(), tableId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This table already has an order");
        }
        User waiter = userRepository.findById(waiterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waiter not found"));
        if (reservation.getTableId() == null) {
            reservation.setTableId(tableId);
        }
        tableRepository.findByIdForUpdate(tableId).ifPresent(table -> {
            if (Boolean.TRUE.equals(table.getIsActive())
                    && table.getStatus() != RestaurantTable.TableStatus.OCCUPIED) {
                table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
            }
        });
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderCode("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setReservation(reservation);
        order.setTableId(tableId);
        order.setWaiter(waiter);
        order.setPublicAccessToken(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.OPEN);
        return toResponse(orderRepository.save(order));
    }

    // Chỉ trả order nghiệp vụ chính; qr_orders được giữ làm lịch sử các lần khách gửi món.
    public List<OrderResponse> getOrders(boolean activeOnly) {
        List<OrderResponse> orders = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
        if (!activeOnly) return orders;

        // Danh sách active chỉ giữ order đang phục vụ hoặc còn tiền chưa thanh toán.
        // Order đã hủy, hoặc đã phục vụ và thanh toán, được chuyển sang lịch sử Revenue.
        return orders.stream().filter(this::isActiveOrder).toList();
    }

    @Transactional(readOnly = true)
    // Gom danh sách phẳng thành reservation -> nhiều order bàn -> một bill dùng chung.
    public List<OrderGroupResponse> getGroups(boolean activeOnly) {
        Map<Long, List<OrderResponse>> grouped = new LinkedHashMap<>();
        getOrders(false).stream()
                .filter(order -> order.reservationId() != null)
                .forEach(order -> grouped.computeIfAbsent(order.reservationId(), ignored -> new ArrayList<>()).add(order));

        grouped.keySet().forEach(paymentService::syncOpenReservationBill);

        return grouped.entrySet().stream()
                .map(entry -> toGroupResponse(entry.getKey(), entry.getValue()))
                .filter(group -> !activeOnly || isActiveGroup(group))
                .toList();
    }

    @Transactional
    public OrderGroupResponse getGroup(Long reservationId) {
        List<OrderResponse> orders = getOrders(false).stream()
                .filter(order -> Objects.equals(order.reservationId(), reservationId))
                .toList();
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order group not found");
        }
        paymentService.syncOpenReservationBill(reservationId);
        return toGroupResponse(reservationId, orders);
    }

    // Tính tổng order chưa hủy và lấy hóa đơn chung để dựng response cấp nhóm.
    private OrderGroupResponse toGroupResponse(Long reservationId, List<OrderResponse> orders) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        BigDecimal subtotal = orders.stream()
                .filter(order -> order.status() != OrderStatus.CANCELLED)
                .map(OrderResponse::subtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BillResponse bill = billRepository.findByReservation_ReservationId(reservationId)
                .map(this::toBillResponse)
                .orElse(null);
        BigDecimal discount = bill == null ? BigDecimal.ZERO : bill.discountAmount();
        BigDecimal total = bill == null ? subtotal : bill.total();
        LocalDateTime createdAt = orders.stream().map(OrderResponse::createdAt).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime updatedAt = orders.stream().map(OrderResponse::updatedAt).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        return new OrderGroupResponse(
                reservationId,
                reservation == null ? orders.get(0).reservationGuestName() : reservation.getFullName(),
                reservation == null ? orders.get(0).reservationStatus() : reservation.getStatus(),
                subtotal,
                discount,
                total,
                bill,
                orders,
                createdAt,
                updatedAt);
    }

    // Chọn giao dịch PENDING/PAID mới nhất để ghép thông tin thanh toán vào bill.
    public BillResponse toBillResponse(Bill bill) {
        Payment payment = switch (bill.getStatus()) {
            case PENDING -> paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(
                    bill.getId(), PaymentStatus.PENDING).orElse(null);
            case PAID -> paymentRepository.findFirstByBill_IdAndStatusOrderByCreatedAtDesc(
                    bill.getId(), PaymentStatus.PAID).orElse(null);
            default -> null;
        };
        Promotion promotion = bill.getPromotion();
        return new BillResponse(
                bill.getId(),
                bill.getBillCode(),
                bill.getReservation().getReservationId(),
                bill.getStatus().name(),
                promotion == null ? null : promotion.getId(),
                promotion == null ? null : promotion.getCode(),
                promotion == null ? null : promotion.getPromotionName(),
                bill.getSubtotal(),
                bill.getDiscountAmount(),
                bill.getTotal(),
                payment == null ? null : payment.getProvider(),
                payment == null ? bill.getStatus().name() : payment.getStatus().name(),
                payment == null ? null : payment.getPaymentCode(),
                payment == null ? null : payment.getQrImageUrl(),
                payment == null ? bill.getPaidAt() : payment.getPaidAt(),
                bill.getLockedAt(),
                bill.getCreatedAt(),
                bill.getUpdatedAt());
    }

    // Nhóm chỉ active khi khách ARRIVED và còn phục vụ hoặc còn phải thanh toán.
    private boolean isActiveGroup(OrderGroupResponse group) {
        if (group.reservationStatus() != ReservationStatus.ARRIVED) return false;
        boolean serviceInProgress = group.orders().stream().anyMatch(this::isActiveOrder);
        boolean paymentOutstanding = group.total().compareTo(BigDecimal.ZERO) > 0
                && (group.bill() == null || !"PAID".equals(group.bill().status()));
        boolean pendingCompletion = group.bill() != null && "PAID".equals(group.bill().status());
        return serviceInProgress || paymentOutstanding || pendingCompletion;
    }

    // Chuyển QrOrder cũ sang OrderResponse hiện tại để frontend xử lý đồng nhất.
    private OrderResponse qrToResponse(QrOrder qrOrder) {
        List<QrOrderItem> rawItems = qrOrderItemRepository.findByOrderId(qrOrder.getOrderId());
        List<OrderItemResponse> items = rawItems.stream().map(this::qrItemToResponse).toList();

        BigDecimal subtotal = items.stream()
                .filter(item -> isBillableStatus(item.status()))
                .map(OrderItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        RestaurantTable table = qrOrder.getTableId() != null
                ? tableRepository.findById(qrOrder.getTableId()).orElse(null)
                : null;
        String tableNumber = table == null ? null : table.getTableNumber();
        String tableName = table == null ? null : table.getTableName();
        List<Long> tableIds = qrOrder.getTableId() != null ? List.of(qrOrder.getTableId()) : List.of();
        List<String> tableNumbers = tableNumber != null ? List.of(tableNumber) : List.of();
        List<String> tableNames = tableName != null ? List.of(tableName) : List.of();

        String guestName = null;
        ReservationStatus reservationStatus = null;
        if (qrOrder.getReservationId() != null) {
            Reservation reservation = reservationRepository.findById(qrOrder.getReservationId()).orElse(null);
            if (reservation != null) {
                guestName = reservation.getFullName();
                reservationStatus = reservation.getStatus();
            }
        }

        OrderStatus orderStatus = switch (qrOrder.getStatus() == null ? "" : qrOrder.getStatus()) {
            case "CLOSED" -> OrderStatus.CLOSED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            default -> OrderStatus.OPEN;
        };

        return new OrderResponse(
                qrOrder.getOrderId(),
                qrOrder.getOrderCode(),
                qrOrder.getReservationId(),
                guestName,
                reservationStatus,
                qrOrder.getTableId(),
                tableIds,
                tableNumber,
                tableNumbers,
                tableName,
                tableNames,
                table == null || table.getStatus() == null ? null : table.getStatus().name(),
                null, null, null, null,
                orderStatus,
                qrServiceStatus(orderStatus, items),
                qrOrder.getNote(),
                null, null, null,
                subtotal,
                BigDecimal.ZERO,
                subtotal,
                null, null, null, null, null, null,
                items,
                null,
                qrOrder.getCreatedAt(),
                qrOrder.getUpdatedAt());
    }

    // Suy ra trạng thái phục vụ tổng quát của QR order từ trạng thái các món.
    private String qrServiceStatus(OrderStatus orderStatus, List<OrderItemResponse> items) {
        if (orderStatus != OrderStatus.OPEN) return orderStatus.name();
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.DRAFT)) return "HAS_DRAFT";
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.PREPARING)) return "PREPARING";
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.READY)) return "READY";
        if (!items.isEmpty() && items.stream().allMatch(item ->
                item.status() == OrderItemStatus.SERVED || item.status() == OrderItemStatus.CANCELLED
                        || item.status() == OrderItemStatus.VOIDED)) {
            return "SERVED";
        }
        return "OPEN";
    }

    // Bổ sung snapshot Menu còn thiếu trong bản ghi QR cũ trước khi trả về.
    private OrderItemResponse qrItemToResponse(QrOrderItem item) {
        String itemName = null, imageUrl = null, category = null;
        if (item.getItemId() != null) {
            MenuItem mi = menuItemRepository.findById(item.getItemId()).orElse(null);
            if (mi != null) {
                itemName = mi.getName();
                imageUrl = mi.getImageUrl();
                category = mi.getCategory();
            }
        }
        OrderItemStatus status;
        try {
            status = OrderItemStatus.valueOf(item.getItemStatus() == null ? "CONFIRMED" : item.getItemStatus());
        } catch (IllegalArgumentException e) {
            status = OrderItemStatus.CONFIRMED;
        }
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineTotal = item.getSubtotal() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(item.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
        return new OrderItemResponse(
                item.getOrderItemId(), item.getItemId(), itemName, imageUrl, category,
                unitPrice, item.getQuantity(), lineTotal, item.getNote(),
                status, item.getSubmittedAt(), null, null, null, null, null);
    }

    // Order OPEN vẫn active nếu chưa SERVED hoàn toàn hoặc chưa thanh toán xong.
    private boolean isActiveOrder(OrderResponse order) {
        if (order.status() != OrderStatus.OPEN) return false;
        boolean serviceInProgress = !"SERVED".equals(order.serviceStatus());
        boolean paymentOutstanding = order.total().compareTo(BigDecimal.ZERO) > 0
                && !"PAID".equals(order.paymentStatus());
        return serviceInProgress || paymentOutstanding;
    }

    // Truy vấn order nội bộ theo ID và chuyển entity sang DTO.
    public OrderResponse getById(Long orderId) {
        return toResponse(findOrder(orderId));
    }

    // Chọn order đại diện mới nhất của reservation, ưu tiên loại không phải dữ liệu migrate.
    public OrderResponse getByReservation(Long reservationId) {
        return toResponse(findDisplayOrderForReservation(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    // Token chỉ tiết lộ đúng order tương ứng; token sai/hết hiệu lực nhận HTTP 404.
    public OrderResponse getByToken(String token) {
        return toResponse(orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid")));
    }

    @Transactional(readOnly = true)
    // Chỉ trả menu khi token hợp lệ, order còn OPEN và món có thể phục vụ.
    public List<MenuItemResponse> getPublicMenu(String token) {
        RestaurantOrder order = orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid"));
        requireOpen(order);
        return menuService.getAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .filter(item -> "AVAILABLE".equals(item.getAvailability()) || "LIMITED".equals(item.getAvailability()))
                .toList();
    }

    // Nhân viên thêm món theo orderId; bản ghi order được khóa trước khi thay đổi.
    public OrderResponse addItem(Long orderId, AddOrderItemRequest request) {
        return addItem(findOrderForUpdate(orderId), request);
    }

    // Khách thêm món theo token nhưng dùng chung toàn bộ business rule với nhân viên.
    public OrderResponse addPublicItem(String token, AddOrderItemRequest request) {
        return addItem(findOrderByTokenForUpdate(token), request);
    }

    // Kiểm tra order/menu/giá rồi thêm mới hoặc gộp vào dòng DRAFT tương đương.
    private OrderResponse addItem(RestaurantOrder order, AddOrderItemRequest request) {
        requireOpen(order);
        MenuItem menuItem = findActiveMenuItem(request.getMenuItemId());
        BigDecimal unitPrice = getMenuPrice(menuItem);
        String note = normalize(request.getNote());

        // Bấm lặp cùng món, cùng note và cùng giá sẽ cộng quantity thay vì tạo dòng DRAFT mới.
        OrderItem existingDraft = order.getItems().stream()
                .filter(item -> item.getStatus() == OrderItemStatus.DRAFT)
                .filter(item -> item.getMenuItem().getId().equals(menuItem.getId()))
                .filter(item -> Objects.equals(normalize(item.getNote()), note))
                .filter(item -> samePrice(item.getUnitPrice(), unitPrice))
                .findFirst()
                .orElse(null);
        if (existingDraft != null) {
            int combinedQuantity = existingDraft.getQuantity() + request.getQuantity();
            if (combinedQuantity > 99) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Combined quantity must not exceed 99");
            }
            existingDraft.setQuantity(combinedQuantity);
            updateSubtotal(existingDraft);
            return toResponse(orderRepository.save(order));
        }

        // Chụp snapshot món và giá vào OrderItem để lịch sử không phụ thuộc Menu về sau.
        OrderItem item = new OrderItem();
        item.setMenuItem(menuItem);
        item.setMenuItemName(menuItem.getName());
        item.setMenuItemImageUrl(menuItem.getImageUrl());
        item.setCategoryName(menuItem.getCategory());
        item.setUnitPrice(unitPrice);
        item.setQuantity(request.getQuantity());
        updateSubtotal(item);
        item.setNote(note);
        item.setStatus(OrderItemStatus.DRAFT);
        order.addItem(item);
        return toResponse(orderRepository.save(order));
    }

    // Nhân viên cập nhật món sau khi khóa order để tránh lost update.
    public OrderResponse updateItem(Long orderId, Long itemId, UpdateOrderItemRequest request) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        return updateItem(order, itemId, request, false);
    }

    // Khách cập nhật qua token; cờ publicAccess giới hạn quyền chỉ ở trạng thái DRAFT.
    public OrderResponse updatePublicItem(String token, Long itemId, UpdateOrderItemRequest request) {
        RestaurantOrder order = findOrderByTokenForUpdate(token);
        return updateItem(order, itemId, request, true);
    }

    // DRAFT cho cả khách/nhân viên sửa; CONFIRMED chỉ nhân viên được sửa.
    private OrderResponse updateItem(
            RestaurantOrder order, Long itemId, UpdateOrderItemRequest request, boolean publicAccess) {
        requireOpen(order);
        OrderItem item = findItem(order, itemId);
        if (item.getStatus() == OrderItemStatus.DRAFT) {
            item.setQuantity(request.getQuantity());
            updateSubtotal(item);
            item.setNote(normalize(request.getNote()));
            return toResponse(orderRepository.save(order));
        }
        if (publicAccess || item.getStatus() != OrderItemStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or confirmed items can be edited");
        }
        item.setQuantity(request.getQuantity());
        updateSubtotal(item);
        item.setNote(normalize(request.getNote()));
        return toResponse(orderRepository.save(order));
    }

    // Nhân viên có thể xóa DRAFT hoặc hủy CONFIRMED chưa chế biến.
    public OrderResponse removeItem(Long orderId, Long itemId) {
        return removeItem(findOrderForUpdate(orderId), itemId, false);
    }

    // Khách chỉ có quyền loại bỏ món còn DRAFT.
    public OrderResponse removePublicItem(String token, Long itemId) {
        return removeItem(findOrderByTokenForUpdate(token), itemId, true);
    }

    // Xóa DRAFT khỏi collection sẽ kích hoạt orphanRemoval; CONFIRMED giữ dòng và đổi CANCELLED.
    private OrderResponse removeItem(RestaurantOrder order, Long itemId, boolean publicAccess) {
        requireOpen(order);
        OrderItem item = findItem(order, itemId);
        if (item.getStatus() == OrderItemStatus.DRAFT) {
            order.getItems().remove(item);
        } else if (!publicAccess && item.getStatus() == OrderItemStatus.CONFIRMED) {
            item.setStatus(OrderItemStatus.CANCELLED);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item can no longer be removed");
        }
        return toResponse(orderRepository.save(order));
    }

    // Điểm vào submit dành cho nhân viên.
    public OrderResponse voidServedItem(Long orderId, Long itemId, VoidOrderItemRequest request) {
        requireAnyRole(PAYMENT_ADJUSTMENT_ROLES);
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);

        OrderItem item = findItem(order, itemId);
        if (item.getStatus() != OrderItemStatus.SERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only served items can be voided from the bill");
        }

        User currentUser = currentUserRequired();
        LocalDateTime voidedAt = LocalDateTime.now();
        String reason = normalize(request.getReason());
        order.getItems().stream()
                .filter(candidate -> isEquivalentOrderItem(candidate, item))
                .toList()
                .forEach(candidate -> {
                    candidate.setStatus(OrderItemStatus.VOIDED);
                    candidate.setVoidReason(reason);
                    candidate.setVoidedAt(voidedAt);
                    candidate.setVoidedBy(currentUser.getFullName());
                });

        if (order.getPromotion() != null) {
            refreshDiscount(order);
        }
        orderRepository.save(order);
        paymentService.syncReservationBillAfterItemVoid(order.getReservation().getReservationId());
        return toResponse(order);
    }

    public OrderResponse submit(Long orderId) {
        return submit(findOrderForUpdate(orderId));
    }

    // Điểm vào submit dành cho khách quét QR.
    public OrderResponse submitPublic(String token) {
        return submit(findOrderByTokenForUpdate(token));
    }

    // Xác nhận cùng lúc toàn bộ DRAFT; không có DRAFT thì trả 409 để tránh submit rỗng.
    private OrderResponse submit(RestaurantOrder order) {
        requireOpen(order);
        List<OrderItem> drafts = order.getItems().stream()
                .filter(item -> item.getStatus() == OrderItemStatus.DRAFT)
                .toList();
        if (drafts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order has no draft items to submit");
        }
        for (OrderItem item : drafts) submitItem(item);
        return toResponse(orderRepository.save(order));
    }

    // Chuyển trạng thái món theo đúng thứ tự và chỉ cho các role phục vụ được phép.
    public OrderResponse updateItemStatus(Long orderId, Long itemId, OrderItemStatus target) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        OrderItem item = findItem(order, itemId);

        if (target == OrderItemStatus.PREPARING
                || target == OrderItemStatus.READY
                || target == OrderItemStatus.SERVED) {
            requireAnyRole(STAFF_ROLES);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status update");
        }

        boolean valid = (item.getStatus() == OrderItemStatus.CONFIRMED && target == OrderItemStatus.PREPARING)
                || (item.getStatus() == OrderItemStatus.PREPARING && target == OrderItemStatus.READY)
                || (item.getStatus() == OrderItemStatus.READY && target == OrderItemStatus.SERVED);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid item transition from " + item.getStatus() + " to " + target);
        }
        // Response gộp các dòng tương đương thành một món hiển thị. Vì vậy mọi dòng DB tương đương
        // phải chuyển cùng lúc để một thao tác không làm số lượng hiển thị bị chia ra nhiều trạng thái.
        order.getItems().stream()
                .filter(candidate -> isEquivalentOrderItem(candidate, item))
                .toList()
                .forEach(candidate -> candidate.setStatus(target));
        return toResponse(orderRepository.save(order));
    }

    // Khóa order rồi giao PaymentService tạo giao dịch/QR SePay.
    public OrderResponse createSepayPayment(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        paymentService.createSepayPayment(order.getId());
        return toResponse(order);
    }

    // Khóa order rồi giao PaymentService ghi nhận thanh toán tiền mặt.
    public OrderResponse createCashPayment(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        paymentService.createCashPayment(order.getId());
        return toResponse(order);
    }

    // Đóng luồng order một bàn: mọi món hợp lệ phải SERVED và tổng tiền phải được thanh toán.
    public OrderResponse close(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        boolean hasServedItem = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED
                        && item.getStatus() != OrderItemStatus.CANCELLED
                        && item.getStatus() != OrderItemStatus.VOIDED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before closing");
        }
        refreshDiscount(order);
        if (calculateSubtotal(order).compareTo(BigDecimal.ZERO) > 0
                && paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be paid before closing");
        }
        // Khi đóng thành công, reservation hoàn tất và các bàn chuyển sang CLEANING.
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());
        order.getReservation().setStatus(ReservationStatus.COMPLETED);
        updateAssignedTableStatus(order.getReservation(), RestaurantTable.TableStatus.CLEANING);
        return toResponse(orderRepository.save(order));
    }

    // Đóng toàn bộ order bàn của reservation sau khi bill chung PAID và món đã phục vụ hết.
    public OrderGroupResponse completeReservation(Long reservationId) {
        List<RestaurantOrder> orders = orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(reservationId)
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order group not found");
        }
        Bill bill = billRepository.findByReservation_ReservationId(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bill must be paid before completing reservation"));
        if (bill.getStatus() != com.swp391.api.modules.payment.entity.BillStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill must be paid before completing reservation");
        }
        boolean hasServedItem = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getStatus() != OrderItemStatus.SERVED
                        && item.getStatus() != OrderItemStatus.CANCELLED
                        && item.getStatus() != OrderItemStatus.VOIDED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before completing reservation");
        }

        // Dùng chung một timestamp để mọi order trong nhóm có thời điểm đóng nhất quán.
        LocalDateTime closedAt = LocalDateTime.now();
        orders.forEach(order -> {
            if (order.getStatus() == OrderStatus.OPEN) {
                order.setStatus(OrderStatus.CLOSED);
                order.setClosedAt(closedAt);
            }
        });
        Reservation reservation = orders.get(0).getReservation();
        reservation.setStatus(ReservationStatus.COMPLETED);
        updateAssignedTableStatus(reservation, RestaurantTable.TableStatus.CLEANING);
        orderRepository.saveAll(orders);
        return getGroup(reservationId);
    }
    // Áp mã khuyến mãi vào order theo luồng tương thích cũ.
    public OrderResponse applyPromotion(Long orderId, String code) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        BigDecimal subtotal = calculateSubtotal(order);
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add order items before applying a promotion");
        }

        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion code not found"));
        validatePromotion(promotion, subtotal, order.getPromotion());

        // Nếu đổi sang mã khác, hoàn lượt dùng của mã cũ trước khi tăng lượt mã mới.
        Promotion currentPromotion = order.getPromotion();
        if (currentPromotion != null && !currentPromotion.getId().equals(promotion.getId())) {
            decrementUsedCount(currentPromotion);
        }
        if (currentPromotion == null || !currentPromotion.getId().equals(promotion.getId())) {
            promotion.setUsedCount((promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) + 1);
        }

        order.setPromotion(promotion);
        order.setDiscountAmount(calculateDiscount(promotion, subtotal));
        return toResponse(orderRepository.save(order));
    }

    // Gỡ khuyến mãi, đưa discount về 0 và hoàn một lượt sử dụng.
    public OrderResponse removePromotion(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        if (order.getPromotion() != null) {
            decrementUsedCount(order.getPromotion());
        }
        order.setPromotion(null);
        order.setDiscountAmount(BigDecimal.ZERO);
        return toResponse(orderRepository.save(order));
    }

    // Hủy order chỉ khi chưa có món PREPARING/READY/SERVED.
    public OrderResponse cancel(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        boolean started = order.getItems().stream().anyMatch(item ->
                item.getStatus() == OrderItemStatus.PREPARING
                        || item.getStatus() == OrderItemStatus.READY
                        || item.getStatus() == OrderItemStatus.SERVED);
        if (started) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be cancelled after preparation has started");
        }
        // Giữ các dòng món làm lịch sử nhưng đánh dấu toàn bộ là CANCELLED.
        order.getItems().forEach(item -> {
            item.setStatus(OrderItemStatus.CANCELLED);
        });
        if (order.getPromotion() != null) {
            decrementUsedCount(order.getPromotion());
            order.setPromotion(null);
            order.setDiscountAmount(BigDecimal.ZERO);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setClosedAt(LocalDateTime.now());
        order.getReservation().setStatus(ReservationStatus.CANCELLED);
        updateAssignedTableStatus(order.getReservation(), RestaurantTable.TableStatus.AVAILABLE);
        return toResponse(orderRepository.save(order));
    }

    // Khi submit, làm mới snapshot theo Menu hiện tại rồi chốt giá và thời điểm gửi bếp.
    private void submitItem(OrderItem item) {
        MenuItem menuItem = findActiveMenuItem(item.getMenuItem().getId());
        item.setMenuItemName(menuItem.getName());
        item.setMenuItemImageUrl(menuItem.getImageUrl());
        item.setCategoryName(menuItem.getCategory());
        item.setUnitPrice(getMenuPrice(menuItem));
        updateSubtotal(item);
        item.setStatus(OrderItemStatus.CONFIRMED);
        item.setSubmittedAt(LocalDateTime.now());
    }

    // Giá phải tồn tại và lớn hơn 0 trước khi được chốt vào order.
    private BigDecimal getMenuPrice(MenuItem item) {
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish price is not available");
        }
        return item.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    // Dựng DTO hoàn chỉnh từ entity và dữ liệu liên quan, không trả trực tiếp JPA entity.
    private OrderResponse toResponse(RestaurantOrder order) {
        List<OrderItemResponse> items = consolidateOrderItemResponses(order.getItems());
        BigDecimal subtotal = items.stream()
                .filter(item -> isBillableStatus(item.status()))
                .map(OrderItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Thành tiền không âm: subtotal - discount, làm tròn hai chữ số thập phân.
        BigDecimal discountAmount = order.getPromotion() == null
                ? BigDecimal.ZERO
                : calculateDiscount(order.getPromotion(), subtotal);
        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Reservation reservation = order.getReservation();
        RestaurantTable table = findOrderTable(order);
        List<Long> tableIds = order.getTableId() == null ? List.of() : List.of(order.getTableId());
        List<String> tableNumbers = table == null || table.getTableNumber() == null ? List.of() : List.of(table.getTableNumber());
        List<String> tableNames = table == null || table.getTableName() == null ? List.of() : List.of(table.getTableName());
        Promotion promotion = order.getPromotion();
        Payment payment = paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()).orElse(null);
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                reservation.getReservationId(),
                reservation.getFullName(),
                reservation.getStatus(),
                order.getTableId(),
                tableIds,
                table == null ? null : table.getTableNumber(),
                tableNumbers,
                table == null ? null : table.getTableName(),
                tableNames,
                table == null || table.getStatus() == null ? null : table.getStatus().name(),
                order.getWaiter().getUserId(),
                order.getWaiter().getFullName(),
                order.getPublicAccessToken(),
                "/order-access/" + order.getPublicAccessToken(),
                order.getStatus(),
                serviceStatus(order),
                order.getNote(),
                promotion == null ? null : promotion.getId(),
                promotion == null ? null : promotion.getCode(),
                promotion == null ? null : promotion.getPromotionName(),
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discountAmount,
                total,
                payment == null ? null : payment.getId(),
                payment == null ? null : payment.getProvider(),
                payment == null ? null : payment.getStatus().name(),
                payment == null ? null : payment.getPaymentCode(),
                payment == null ? null : payment.getQrImageUrl(),
                payment == null ? null : payment.getPaidAt(),
                items,
                order.getClosedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    // Tổng order chỉ tính các dòng chưa CANCELLED.
    private BigDecimal calculateSubtotal(RestaurantOrder order) {
        return order.getItems().stream()
                .filter(item -> isBillableStatus(item.getStatus()))
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
    // Tính số tiền giảm theo phần trăm hoặc số tiền cố định.
    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        if (promotion == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        // Công thức phần trăm: subtotal × discountValue / 100.
        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENT
                ? subtotal.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();

        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // Kiểm tra trạng thái, thời hạn, giá trị đơn tối thiểu và giới hạn lượt dùng.
    private void validatePromotion(Promotion promotion, BigDecimal subtotal, Promotion currentPromotion) {
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is outside its valid period");
        }
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total does not meet the minimum bill amount");
        }
        boolean samePromotion = currentPromotion != null && currentPromotion.getId().equals(promotion.getId());
        if (!samePromotion && promotion.getUsageLimit() != null
                && (promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) >= promotion.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion usage limit has been reached");
        }
    }
    // Tính lại discount khi số lượng/món trong order thay đổi.
    private void refreshDiscount(RestaurantOrder order) {
        if (order.getPromotion() == null) {
            order.setDiscountAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal subtotal = calculateSubtotal(order);
        validatePromotion(order.getPromotion(), subtotal, order.getPromotion());
        order.setDiscountAmount(calculateDiscount(order.getPromotion(), subtotal));
    }
    // Hoàn lại một lượt khuyến mãi nhưng không bao giờ để usedCount âm.
    private void decrementUsedCount(Promotion promotion) {
        int usedCount = promotion.getUsedCount() == null ? 0 : promotion.getUsedCount();
        promotion.setUsedCount(Math.max(0, usedCount - 1));
    }

    // Tìm bàn chính để tương thích reservation kiểu cũ chỉ lưu tableId.
    private RestaurantTable findAssignedTable(Reservation reservation) {
        Long tableId = resolvePrimaryTableId(reservation);
        if (tableId == null) return null;
        return tableRepository.findById(tableId).orElse(null);
    }

    // Ưu tiên tableId được chốt trên order, chỉ fallback về bàn của reservation.
    private RestaurantTable findOrderTable(RestaurantOrder order) {
        if (order.getTableId() == null) {
            return findAssignedTable(order.getReservation());
        }
        return tableRepository.findById(order.getTableId()).orElse(null);
    }

    // Hỗ trợ cả quan hệ nhiều bàn mới và trường tableId cũ.
    private List<RestaurantTable> findAssignedTables(Reservation reservation) {
        List<RestaurantTable> tables = reservation.getTables();
        if (tables != null && !tables.isEmpty()) {
            return tables;
        }

        Long tableId = reservation.getTableId();
        if (tableId == null) {
            return List.of();
        }
        return tableRepository.findById(tableId)
                .map(List::of)
                .orElseGet(List::of);
    }

    // Khóa từng bàn đang active rồi cập nhật đồng loạt trạng thái trong cùng transaction.
    private void updateAssignedTableStatus(Reservation reservation, RestaurantTable.TableStatus status) {
        List<Long> tableIds = findAssignedTables(reservation).stream()
                .map(RestaurantTable::getId)
                .distinct()
                .toList();
        if (tableIds.isEmpty()) return;

        List<RestaurantTable> updatedTables = new java.util.ArrayList<>();
        tableIds.forEach(tableId -> tableRepository.findByIdForUpdate(tableId).ifPresent(table -> {
            if (Boolean.TRUE.equals(table.getIsActive())) {
                table.setStatus(status);
                updatedTables.add(table);
            }
        }));
        tableRepository.saveAll(updatedTables);
    }

    // Xác định bàn đại diện: tableId cũ trước, nếu thiếu lấy bàn đầu tiên trong danh sách.
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

    // Ánh xạ một entity OrderItem sang DTO không làm thay đổi dữ liệu đang quản lý bởi JPA.
    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal lineTotal = item.getSubtotal();
        return new OrderItemResponse(
                item.getId(), item.getMenuItem().getId(), item.getMenuItemName(), item.getMenuItemImageUrl(),
                item.getCategoryName(), item.getUnitPrice(), item.getQuantity(), lineTotal, item.getNote(),
                item.getStatus(), item.getSubmittedAt(), item.getVoidReason(), item.getVoidedAt(), item.getVoidedBy(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    // Suy ra trạng thái phục vụ tổng hợp để frontend hiển thị badge của cả order.
    private String serviceStatus(RestaurantOrder order) {
        if (order.getStatus() != OrderStatus.OPEN) return order.getStatus().name();
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.DRAFT)) return "HAS_DRAFT";
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.PREPARING)) return "PREPARING";
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.READY)) return "READY";
        if (!order.getItems().isEmpty() && order.getItems().stream().allMatch(item ->
                item.getStatus() == OrderItemStatus.SERVED || item.getStatus() == OrderItemStatus.CANCELLED
                        || item.getStatus() == OrderItemStatus.VOIDED)) {
            return "SERVED";
        }
        return "OPEN";
    }

    // Truy vấn chỉ đọc theo ID.
    private RestaurantOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    // Truy vấn có khóa pessimistic dùng trước mọi thao tác ghi theo ID.
    private RestaurantOrder findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    // Truy vấn có khóa pessimistic dùng cho thao tác ghi từ trang QR.
    private RestaurantOrder findOrderByTokenForUpdate(String token) {
        return orderRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid"));
    }

    // Chỉ tìm item bên trong order hiện tại để tránh sửa chéo order.
    private OrderItem findItem(RestaurantOrder order, Long itemId) {
        return order.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));
    }

    // Không cho gọi món đã bị ngừng phục vụ trong Menu Management.
    private MenuItem findActiveMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active menu item not found"));
    }

    // Mọi thay đổi món/thanh toán theo order đều yêu cầu order còn OPEN.
    private void requireOpen(RestaurantOrder order) {
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is no longer open");
        }
    }

    // Lấy nhân viên hiện tại từ Spring Security và truy ngược sang User trong DB.
    private User currentUserRequired() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // Kiểm tra quyền nghiệp vụ sâu hơn quyền chung ở Controller.
    private void requireAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(roles::contains);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }

    // Ưu tiên order thật; chỉ dùng bản ghi ORD-MIG-* khi không còn lựa chọn khác.
    private Optional<RestaurantOrder> findDisplayOrderForReservation(Long reservationId) {
        List<RestaurantOrder> orders = orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(reservationId);
        return orders.stream()
                .filter(order -> order.getOrderCode() == null || !order.getOrderCode().startsWith("ORD-MIG-"))
                .findFirst()
                .or(() -> orders.stream().findFirst());
    }

    // Chuẩn hóa chuỗi: trim khoảng trắng và đổi chuỗi rỗng thành null.
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Cập nhật subtotal mỗi khi số lượng hoặc đơn giá thay đổi.
    private void updateSubtotal(OrderItem item) {
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2));
    }

    /**
     * Gộp các dòng tương đương để hiển thị nhưng tuyệt đối không sửa collection JPA đang được quản lý.
     * Hàm phải không có side effect: nếu xóa phần tử khỏi RestaurantOrder.items, orphanRemoval có thể
     * xóa luôn dòng lịch sử tương ứng khi transaction chỉ đọc được flush.
     */
    private List<OrderItemResponse> consolidateOrderItemResponses(List<OrderItem> sourceItems) {
        Map<OrderItemMergeKey, OrderItemResponse> consolidated = new LinkedHashMap<>();

        for (OrderItem item : sourceItems) {
            OrderItemMergeKey key = new OrderItemMergeKey(
                    item.getMenuItem().getId(),
                    item.getStatus(),
                    normalize(item.getNote()),
                    normalizedPrice(item.getUnitPrice()));
            OrderItemResponse current = toItemResponse(item);
            OrderItemResponse existing = consolidated.get(key);
            if (existing == null) {
                consolidated.put(key, withNote(current, key.note()));
                continue;
            }

            consolidated.put(key, new OrderItemResponse(
                    existing.id(),
                    existing.menuItemId(),
                    existing.menuItemName(),
                    existing.menuItemImageUrl(),
                    existing.category(),
                    existing.unitPrice(),
                    existing.quantity() + current.quantity(),
                    existing.lineTotal().add(current.lineTotal()),
                    key.note(),
                    existing.status(),
                    earlier(existing.submittedAt(), current.submittedAt()),
                    existing.voidReason() == null ? current.voidReason() : existing.voidReason(),
                    earlier(existing.voidedAt(), current.voidedAt()),
                    existing.voidedBy() == null ? current.voidedBy() : existing.voidedBy(),
                    earlier(existing.createdAt(), current.createdAt()),
                    later(existing.updatedAt(), current.updatedAt())));
        }

        return new ArrayList<>(consolidated.values());
    }

    // Tạo bản sao DTO với note đã chuẩn hóa mà không sửa entity.
    private OrderItemResponse withNote(OrderItemResponse item, String note) {
        return new OrderItemResponse(
                item.id(), item.menuItemId(), item.menuItemName(), item.menuItemImageUrl(), item.category(),
                item.unitPrice(), item.quantity(), item.lineTotal(), note, item.status(), item.submittedAt(),
                item.voidReason(), item.voidedAt(), item.voidedBy(), item.createdAt(), item.updatedAt());
    }

    // Chọn mốc sớm hơn khi gộp thời gian của nhiều dòng.
    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }

    // Chọn mốc muộn hơn cho updatedAt của dòng đã gộp.
    private LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }


    // BigDecimal.compareTo bỏ qua khác biệt scale như 10.0 và 10.00.
    private boolean samePrice(BigDecimal first, BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    // Hai dòng chỉ tương đương khi cùng món, trạng thái, note và đơn giá.
    private boolean isEquivalentOrderItem(OrderItem candidate, OrderItem reference) {
        return candidate.getMenuItem().getId().equals(reference.getMenuItem().getId())
                && candidate.getStatus() == reference.getStatus()
                && Objects.equals(normalize(candidate.getNote()), normalize(reference.getNote()))
                && samePrice(candidate.getUnitPrice(), reference.getUnitPrice());
    }

    private boolean isBillableStatus(OrderItemStatus status) {
        return status != OrderItemStatus.CANCELLED && status != OrderItemStatus.VOIDED;
    }

    // Bỏ số 0 cuối để giá dùng làm khóa gộp có biểu diễn ổn định.
    private BigDecimal normalizedPrice(BigDecimal price) {
        return price == null ? null : price.stripTrailingZeros();
    }

    // Khóa bất biến dùng để gom các dòng OrderItem khi dựng response.
    private record OrderItemMergeKey(
            Long menuItemId,
            OrderItemStatus status,
            String note,
            BigDecimal unitPrice) {
    }
}
