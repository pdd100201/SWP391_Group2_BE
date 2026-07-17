package com.swp391.api.modules.order.service;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderItemResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import com.swp391.api.modules.qr.entity.QrOrder;
import com.swp391.api.modules.qr.entity.QrOrderItem;
import com.swp391.api.modules.qr.repository.QrOrderItemRepository;
import com.swp391.api.modules.qr.repository.QrOrderRepository;
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
public class OrderService {
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER");

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final MenuService menuService;
    private final PromotionRepository promotionRepository;
    private final PaymentRepository paymentRepository;
    private final QrOrderRepository qrOrderRepository;
    private final QrOrderItemRepository qrOrderItemRepository;

    public OrderService(
            OrderRepository orderRepository,
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            MenuItemRepository menuItemRepository,
            UserRepository userRepository,
            MenuService menuService,
            PromotionRepository promotionRepository,
            PaymentRepository paymentRepository,
            QrOrderRepository qrOrderRepository,
            QrOrderItemRepository qrOrderItemRepository) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.menuService = menuService;
        this.promotionRepository = promotionRepository;
        this.paymentRepository = paymentRepository;
        this.qrOrderRepository = qrOrderRepository;
        this.qrOrderItemRepository = qrOrderItemRepository;
    }

    public OrderResponse create(CreateOrderRequest request) {
        User waiter = currentUserRequired();
        Reservation reservation = reservationRepository.findByIdForUpdate(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ARRIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be checked in before opening an order");
        }
        Long assignedTableId = resolvePrimaryTableId(reservation);
        if (assignedTableId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation must be assigned to a table before opening an order");
        }
        if (reservation.getTableId() == null) {
            reservation.setTableId(assignedTableId);
        }
        if (orderRepository.findByReservationReservationId(reservation.getReservationId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation already has an order");
        }
        RestaurantTable table = tableRepository.findByIdForUpdate(assignedTableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned table not found"));
        if (!Boolean.TRUE.equals(table.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned table is not active");
        }
        if (table.getStatus() != RestaurantTable.TableStatus.OCCUPIED) {
            table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        }

        RestaurantOrder order = new RestaurantOrder();
        order.setOrderCode("ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setReservation(reservation);
        order.setWaiter(waiter);
        order.setPublicAccessToken(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.OPEN);
        order.setNote(normalize(request.getNote()));
        return toResponse(orderRepository.save(order));
    }

    public List<OrderResponse> getOrders(boolean activeOnly) {
        List<OrderResponse> orders = new ArrayList<>();
        orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).forEach(orders::add);
        qrOrderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::qrToResponse).forEach(orders::add);
        orders.sort((a, b) -> {
            LocalDateTime ca = a.createdAt(), cb = b.createdAt();
            if (ca == null && cb == null) return 0;
            if (ca == null) return 1;
            if (cb == null) return -1;
            return cb.compareTo(ca);
        });
        if (!activeOnly) return orders;

        // Active Orders chỉ chứa order vẫn đang được phục vụ hoặc chưa thanh toán.
        // Order bị hủy luôn bị loại; Order đã phục vụ và đã thanh toán sẽ được quản lý ở lịch sử tổng.
        return orders.stream().filter(this::isActiveOrder).toList();
    }

    private OrderResponse qrToResponse(QrOrder qrOrder) {
        List<QrOrderItem> rawItems = qrOrderItemRepository.findByOrderId(qrOrder.getOrderId());
        List<OrderItemResponse> items = rawItems.stream().map(this::qrItemToResponse).toList();

        BigDecimal subtotal = items.stream()
                .filter(item -> item.status() != OrderItemStatus.CANCELLED)
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

    private String qrServiceStatus(OrderStatus orderStatus, List<OrderItemResponse> items) {
        if (orderStatus != OrderStatus.OPEN) return orderStatus.name();
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.DRAFT)) return "HAS_DRAFT";
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.PREPARING)) return "PREPARING";
        if (items.stream().anyMatch(item -> item.status() == OrderItemStatus.READY)) return "READY";
        if (!items.isEmpty() && items.stream().allMatch(item ->
                item.status() == OrderItemStatus.SERVED || item.status() == OrderItemStatus.CANCELLED)) {
            return "SERVED";
        }
        return "OPEN";
    }

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
                status, item.getSubmittedAt(), null, null);
    }

    private boolean isActiveOrder(OrderResponse order) {
        if (order.status() != OrderStatus.OPEN) return false;
        boolean serviceInProgress = !"SERVED".equals(order.serviceStatus());
        boolean paymentOutstanding = order.total().compareTo(BigDecimal.ZERO) > 0
                && !"PAID".equals(order.paymentStatus());
        return serviceInProgress || paymentOutstanding;
    }

    public OrderResponse getById(Long orderId) {
        return toResponse(findOrder(orderId));
    }

    public OrderResponse getByReservation(Long reservationId) {
        return toResponse(orderRepository.findByReservationReservationId(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    public OrderResponse getByToken(String token) {
        return toResponse(orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid")));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getPublicMenu(String token) {
        RestaurantOrder order = orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid"));
        requireOpen(order);
        return menuService.getAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .filter(item -> "AVAILABLE".equals(item.getAvailability()) || "LIMITED".equals(item.getAvailability()))
                .toList();
    }

    public OrderResponse addItem(Long orderId, AddOrderItemRequest request) {
        return addItem(findOrderForUpdate(orderId), request);
    }

    public OrderResponse addPublicItem(String token, AddOrderItemRequest request) {
        return addItem(findOrderByTokenForUpdate(token), request);
    }

    private OrderResponse addItem(RestaurantOrder order, AddOrderItemRequest request) {
        requireOpen(order);
        MenuItem menuItem = findActiveMenuItem(request.getMenuItemId());
        BigDecimal unitPrice = getMenuPrice(menuItem);
        String note = normalize(request.getNote());

        // A repeated click for the same draft dish updates its quantity instead of creating another row.
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

    public OrderResponse updateItem(Long orderId, Long itemId, UpdateOrderItemRequest request) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        return updateItem(order, itemId, request, false);
    }

    public OrderResponse updatePublicItem(String token, Long itemId, UpdateOrderItemRequest request) {
        RestaurantOrder order = findOrderByTokenForUpdate(token);
        return updateItem(order, itemId, request, true);
    }

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

    public OrderResponse removeItem(Long orderId, Long itemId) {
        return removeItem(findOrderForUpdate(orderId), itemId, false);
    }

    public OrderResponse removePublicItem(String token, Long itemId) {
        return removeItem(findOrderByTokenForUpdate(token), itemId, true);
    }

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

    public OrderResponse submit(Long orderId) {
        return submit(findOrderForUpdate(orderId));
    }

    public OrderResponse submitPublic(String token) {
        return submit(findOrderByTokenForUpdate(token));
    }

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

    public OrderResponse updateItemStatus(Long orderId, Long itemId, OrderItemStatus target) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        OrderItem item = findItem(order, itemId);

        if (target == OrderItemStatus.PREPARING || target == OrderItemStatus.READY) {
            requireAnyRole(Set.of("ROLE_ADMIN", "ROLE_MANAGER"));
        } else if (target == OrderItemStatus.SERVED) {
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
        item.setStatus(target);
        return toResponse(orderRepository.save(order));
    }

    public OrderResponse close(Long orderId) {
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        boolean hasServedItem = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED && item.getStatus() != OrderItemStatus.CANCELLED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before closing");
        }
        refreshDiscount(order);
        if (calculateSubtotal(order).compareTo(BigDecimal.ZERO) > 0
                && paymentRepository.findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PAID).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be paid before closing");
        }
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());
        order.getReservation().setStatus(ReservationStatus.COMPLETED);
        updateAssignedTableStatus(order.getReservation(), RestaurantTable.TableStatus.CLEANING);
        return toResponse(orderRepository.save(order));
    }

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

    private BigDecimal getMenuPrice(MenuItem item) {
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish price is not available");
        }
        return item.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    private OrderResponse toResponse(RestaurantOrder order) {
        List<OrderItemResponse> items = consolidateOrderItems(order.getItems()).stream()
                .map(this::toItemResponse).toList();
        BigDecimal subtotal = items.stream()
                .filter(item -> item.status() != OrderItemStatus.CANCELLED)
                .map(OrderItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = order.getPromotion() == null
                ? BigDecimal.ZERO
                : calculateDiscount(order.getPromotion(), subtotal);
        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Reservation reservation = order.getReservation();
        RestaurantTable table = findAssignedTable(reservation);
        List<RestaurantTable> assignedTables = findAssignedTables(reservation);
        Promotion promotion = order.getPromotion();
        Payment payment = paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()).orElse(null);
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                reservation.getReservationId(),
                reservation.getFullName(),
                reservation.getStatus(),
                reservation.getTableId(),
                assignedTables.stream().map(RestaurantTable::getId).toList(),
                table == null ? null : table.getTableNumber(),
                assignedTables.stream().map(RestaurantTable::getTableNumber).toList(),
                table == null ? null : table.getTableName(),
                assignedTables.stream().map(RestaurantTable::getTableName).toList(),
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
                payment == null ? null : payment.getProvider().name(),
                payment == null ? null : payment.getStatus().name(),
                payment == null ? null : payment.getPaymentCode(),
                payment == null ? null : payment.getQrImageUrl(),
                payment == null ? null : payment.getPaidAt(),
                items,
                order.getClosedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private BigDecimal calculateSubtotal(RestaurantOrder order) {
        return order.getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
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
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

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

    private void refreshDiscount(RestaurantOrder order) {
        if (order.getPromotion() == null) {
            order.setDiscountAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal subtotal = calculateSubtotal(order);
        validatePromotion(order.getPromotion(), subtotal, order.getPromotion());
        order.setDiscountAmount(calculateDiscount(order.getPromotion(), subtotal));
    }

    private void decrementUsedCount(Promotion promotion) {
        int usedCount = promotion.getUsedCount() == null ? 0 : promotion.getUsedCount();
        promotion.setUsedCount(Math.max(0, usedCount - 1));
    }

    private RestaurantTable findAssignedTable(Reservation reservation) {
        Long tableId = resolvePrimaryTableId(reservation);
        if (tableId == null) return null;
        return tableRepository.findById(tableId).orElse(null);
    }

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

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal lineTotal = item.getSubtotal();
        return new OrderItemResponse(
                item.getId(), item.getMenuItem().getId(), item.getMenuItemName(), item.getMenuItemImageUrl(),
                item.getCategoryName(), item.getUnitPrice(), item.getQuantity(), lineTotal, item.getNote(),
                item.getStatus(), item.getSubmittedAt(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private String serviceStatus(RestaurantOrder order) {
        if (order.getStatus() != OrderStatus.OPEN) return order.getStatus().name();
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.DRAFT)) return "HAS_DRAFT";
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.PREPARING)) return "PREPARING";
        if (order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.READY)) return "READY";
        if (!order.getItems().isEmpty() && order.getItems().stream().allMatch(item ->
                item.getStatus() == OrderItemStatus.SERVED || item.getStatus() == OrderItemStatus.CANCELLED)) {
            return "SERVED";
        }
        return "OPEN";
    }

    private RestaurantOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private RestaurantOrder findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private RestaurantOrder findOrderByTokenForUpdate(String token) {
        return orderRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid"));
    }

    private OrderItem findItem(RestaurantOrder order, Long itemId) {
        return order.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));
    }

    private MenuItem findActiveMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active menu item not found"));
    }

    private void requireOpen(RestaurantOrder order) {
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is no longer open");
        }
    }

    private User currentUserRequired() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void requireAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(roles::contains);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateSubtotal(OrderItem item) {
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2));
    }

    /**
     * Consolidates duplicate rows already present in old orders and duplicates that become identical
     * after submission or a status transition. Notes and snapshot prices are part of the key so that
     * special requests and historical totals are never lost.
     */
    private List<OrderItem> consolidateOrderItems(List<OrderItem> source) {
        Map<OrderItemMergeKey, OrderItem> consolidated = new LinkedHashMap<>();

        for (OrderItem item : source) {
            OrderItemMergeKey key = new OrderItemMergeKey(
                    item.getMenuItem().getId(),
                    item.getStatus(),
                    normalize(item.getNote()),
                    normalizedPrice(item.getUnitPrice()));
            OrderItem existing = consolidated.get(key);
            if (existing == null) {
                // Clone to avoid mutating the managed entity
                OrderItem copy = new OrderItem();
                copy.setMenuItem(item.getMenuItem());
                copy.setMenuItemName(item.getMenuItemName());
                copy.setMenuItemImageUrl(item.getMenuItemImageUrl());
                copy.setCategoryName(item.getCategoryName());
                copy.setUnitPrice(item.getUnitPrice());
                copy.setQuantity(item.getQuantity());
                copy.setSubtotal(item.getSubtotal());
                copy.setNote(key.note());
                copy.setStatus(item.getStatus());
                copy.setSubmittedAt(item.getSubmittedAt());
                consolidated.put(key, copy);
            } else {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                updateSubtotal(existing);
            }
        }

        return new ArrayList<>(consolidated.values());
    }

    private boolean samePrice(BigDecimal first, BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    private BigDecimal normalizedPrice(BigDecimal price) {
        return price == null ? null : price.stripTrailingZeros();
    }

    private record OrderItemMergeKey(
            Long menuItemId,
            OrderItemStatus status,
            String note,
            BigDecimal unitPrice) {
    }
}
