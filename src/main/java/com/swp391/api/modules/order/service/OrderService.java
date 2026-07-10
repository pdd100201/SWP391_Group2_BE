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
import java.util.List;
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
    // Sai số nhỏ cho các phép tính tồn kho kiểu double.
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER");

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final MenuService menuService;
    private final PromotionRepository promotionRepository;

    public OrderService(
            OrderRepository orderRepository,
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            MenuItemRepository menuItemRepository,
            UserRepository userRepository,
            MenuService menuService,
            PromotionRepository promotionRepository) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.menuService = menuService;
        this.promotionRepository = promotionRepository;
    }

    public OrderResponse create(CreateOrderRequest request) {
        // Nhân viên mở order từ reservation đã arrived/confirmed và đã được gán bàn.
        User waiter = currentUserRequired();
        Reservation reservation = reservationRepository.findByIdForUpdate(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ARRIVED && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed or checked-in reservations can open an order");
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
        // Lock the table so two staff actions cannot change its status at the same time.
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

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(boolean activeOnly) {
        // Màn nhân viên thường gọi activeOnly=true để loại các đơn đã đóng.
        List<RestaurantOrder> orders = activeOnly
                ? orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.OPEN)
                : orderRepository.findAllByOrderByCreatedAtDesc();
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long orderId) {
        return toResponse(findOrder(orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByReservation(Long reservationId) {
        return toResponse(orderRepository.findByReservationReservationId(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByToken(String token) {
        return toResponse(orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid")));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getPublicMenu(String token) {
        // Menu công khai chỉ hợp lệ khi token trỏ tới một order đang mở.
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
        // Món bắt đầu ở DRAFT để nhân viên/khách còn sửa trước khi tồn kho bị trừ.
        requireOpen(order);
        MenuItem menuItem = findActiveMenuItem(request.getMenuItemId());

        OrderItem item = new OrderItem();
        item.setMenuItem(menuItem);
        item.setMenuItemName(menuItem.getName());
        item.setMenuItemImageUrl(menuItem.getImageUrl());
        item.setCategoryName(menuItem.getCategory());
        item.setUnitPrice(menuItemPrice(menuItem));
        item.setQuantity(request.getQuantity());
        updateSubtotal(item);
        item.setNote(normalize(request.getNote()));
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
        // Người dùng công khai chỉ sửa được món DRAFT; nhân viên có thể chỉnh số lượng CONFIRMED.
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
        adjustConfirmedQuantity(item, request.getQuantity());
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
        // Xóa món đã confirmed bởi nhân viên sẽ hủy món và hoàn tồn kho đã trừ.
        requireOpen(order);
        OrderItem item = findItem(order, itemId);
        if (item.getStatus() == OrderItemStatus.DRAFT) {
            order.getItems().remove(item);
        } else if (!publicAccess && item.getStatus() == OrderItemStatus.CONFIRMED) {
            restoreInventory(item);
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

    public OrderResponse addAndSubmitPublicItemsForTable(Long tableId, List<AddOrderItemRequest> requests) {
        // Luồng QR bàn: thêm món vào order OPEN hiện tại của bàn và submit ngay.
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }

        RestaurantOrder order = findOpenOrderByTableForUpdate(tableId);
        requireOpen(order);

        List<OrderItem> createdItems = new ArrayList<>();
        for (AddOrderItemRequest request : requests) {
            MenuItem menuItem = findActiveMenuItem(request.getMenuItemId());

            OrderItem item = new OrderItem();
            item.setMenuItem(menuItem);
            item.setMenuItemName(menuItem.getName());
            item.setMenuItemImageUrl(menuItem.getImageUrl());
            item.setCategoryName(menuItem.getCategory());
            item.setUnitPrice(menuItemPrice(menuItem));
            item.setQuantity(request.getQuantity());
            updateSubtotal(item);
            item.setNote(normalize(request.getNote()));
            item.setStatus(OrderItemStatus.DRAFT);
            order.addItem(item);
            createdItems.add(item);
        }

        for (OrderItem item : createdItems) {
            submitItem(item);
        }
        return toResponse(orderRepository.save(order));
    }

    private OrderResponse submit(RestaurantOrder order) {
        // Chỉ submit món DRAFT; món confirmed/preparing/served giữ nguyên.
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
        // Đổi trạng thái bếp/phục vụ được kiểm soát theo vai trò và bước chuyển hợp lệ.
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
        // Chỉ được đóng order khi mọi món không bị hủy đều đã phục vụ.
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        boolean hasServedItem = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED && item.getStatus() != OrderItemStatus.CANCELLED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before closing");
        }
        refreshDiscount(order);
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
        // Không cho hủy order khi đã bắt đầu chế biến.
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
            if (item.getStatus() == OrderItemStatus.CONFIRMED) restoreInventory(item);
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
        item.setUnitPrice(menuItemPrice(menuItem));
        updateSubtotal(item);
        item.setStatus(OrderItemStatus.CONFIRMED);
        item.setSubmittedAt(LocalDateTime.now());
    }

    private void adjustConfirmedQuantity(OrderItem item, int newQuantity) {
        if (newQuantity == item.getQuantity()) return;
        item.setQuantity(newQuantity);
        updateSubtotal(item);
    }

    private void restoreInventory(OrderItem item) {
    }

    private BigDecimal menuItemPrice(MenuItem item) {
        return item.getPrice() == null ? BigDecimal.ZERO : item.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    private OrderResponse toResponse(RestaurantOrder order) {
        // Dùng một dạng dữ liệu trả về thống nhất cho giao diện nhân viên và giao diện công khai.
        List<OrderItemResponse> items = order.getItems().stream().map(this::toItemResponse).toList();
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

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal lineTotal = item.getSubtotal();
        return new OrderItemResponse(
                item.getId(), item.getMenuItem().getId(), item.getMenuItemName(), item.getMenuItemImageUrl(),
                item.getCategoryName(), item.getUnitPrice(), item.getQuantity(), lineTotal, item.getNote(),
                item.getStatus(), item.getSubmittedAt(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private String serviceStatus(RestaurantOrder order) {
        // Trạng thái suy ra dùng để tóm tắt tình trạng món cho UI danh sách order.
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

    private RestaurantOrder findOpenOrderByTableForUpdate(Long tableId) {
        return orderRepository.findOpenOrdersByTableIdForUpdate(tableId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "This table does not have an open order"
                ));
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
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP));
    }
}
