package com.swp391.api.modules.qr.service.impl;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.entity.RecipeIngredient;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.qr.dto.*;
import com.swp391.api.modules.qr.dto.QrAccessTokenResponse;
import com.swp391.api.modules.qr.dto.QrOrderSummaryResponse;
import com.swp391.api.modules.qr.entity.*;
import com.swp391.api.modules.qr.repository.*;
import com.swp391.api.modules.qr.service.QrService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class QrServiceImpl implements QrService {

    private static final double EPSILON = 0.000001;

    private final QrSessionRepository sessionRepository;
    private final QrMenuItemRepository menuItemRepository;
    private final QrRecipeIngredientRepository recipeIngredientRepository;
    private final MenuItemRepository menuModuleItemRepository;
    private final QrOrderRepository orderRepository;
    private final QrOrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderService orderService;
    private final ReservationRepository reservationRepository;
    private final QrDiningTableRepository diningTableRepository;
    private final OrderRepository restaurantOrderRepository;

    public QrServiceImpl(
            QrSessionRepository sessionRepository,
            QrMenuItemRepository menuItemRepository,
            QrRecipeIngredientRepository recipeIngredientRepository,
            MenuItemRepository menuModuleItemRepository,
            QrOrderRepository orderRepository,
            QrOrderItemRepository orderItemRepository,
            InventoryRepository inventoryRepository,
            OrderService orderService,
            ReservationRepository reservationRepository,
            QrDiningTableRepository diningTableRepository,
            OrderRepository restaurantOrderRepository) {
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.menuModuleItemRepository = menuModuleItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderService = orderService;
        this.reservationRepository = reservationRepository;
        this.diningTableRepository = diningTableRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
    }

    @Override
    @Transactional
    public QrSessionResponse createSession(Long tableId) {
        QrSession session = new QrSession();
        session.setTableId(tableId);
        session.setSessionToken(UUID.randomUUID().toString());
        session.setStartedAt(LocalDateTime.now());
        session.setStatus("ACTIVE");

        sessionRepository.save(session);

        return new QrSessionResponse(
                session.getSessionToken(),
                tableId,
                String.valueOf(tableId),
                session.getExpiredAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QrMenuResponse getMenu() {
        List<QrMenuItem> activeItems = menuItemRepository.findByIsActive(true);

        // Tính foodCost cho tất cả món trong một query
        Set<Long> itemIds = activeItems.stream()
                .map(QrMenuItem::getId)
                .collect(Collectors.toSet());

        Map<Long, Double> foodCostMap = recipeIngredientRepository
                .findFoodCostsByMenuItemIds(itemIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
                ));

        Map<Long, Integer> canServeMap = recipeIngredientRepository
                .findCanServeByMenuItemIds(itemIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> row[1] != null ? ((Number) row[1]).intValue() : 0
                ));

        Map<String, List<QrMenuItem>> itemsByCategory = activeItems.stream()
                .collect(Collectors.groupingBy(QrMenuItem::getCategory));

        List<QrMenuResponse.CategoryDto> categories = itemsByCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<QrMenuResponse.ItemDto> itemDtos = entry.getValue().stream()
                            .sorted(Comparator.comparing(QrMenuItem::getName, String.CASE_INSENSITIVE_ORDER))
                            .map(item -> new QrMenuResponse.ItemDto(
                                    item.getId(),
                                    item.getName(),
                                    computePrice(item, foodCostMap.getOrDefault(item.getId(), 0.0)),
                                    item.getImageUrl(),
                                    item.getDescription(),
                                    canServeMap.getOrDefault(item.getId(), 99)
                            ))
                            .collect(Collectors.toList());

                    return new QrMenuResponse.CategoryDto(null, entry.getKey(), itemDtos);
                })
                .collect(Collectors.toList());

        return new QrMenuResponse(categories);
    }

    @Override
    @Transactional
    public QrOrderResponse createOrder(QrOrderRequest request) {
        QrSession session = sessionRepository
                .findBySessionTokenAndStatus(request.getSessionToken(), "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Invalid or expired session token"));

        // Reuse existing OPEN order for this table instead of creating a new one
        QrOrder order = orderRepository.findFirstByTableIdAndStatus(session.getTableId(), "OPEN")
                .orElseGet(() -> {
                    String qrPrefix = "ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-QR";
                    long qrSeq = orderRepository.countByOrderCodeStartingWith(qrPrefix) + 1;
                    QrOrder newOrder = new QrOrder();
                    newOrder.setOrderCode(qrPrefix + String.format("%06d", qrSeq));
                    newOrder.setTableId(session.getTableId());
                    newOrder.setOrderType("QR_ORDER");
                    newOrder.setStatus("OPEN");
                    newOrder.setCreatedAt(LocalDateTime.now());
                    newOrder.setUpdatedAt(LocalDateTime.now());
                    reservationRepository.findByTableIdAndStatus(session.getTableId(), ReservationStatus.CONFIRMED)
                            .ifPresent(r -> newOrder.setReservationId(r.getReservationId()));
                    return orderRepository.save(newOrder);
                });

        for (QrOrderRequest.OrderItemDto itemDto : request.getItems()) {
            QrMenuItem menuItem = menuItemRepository.findById(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemDto.getItemId()));
            Long mainItemId = menuModuleItemRepository.getReferenceById(menuItem.getId()).getId();

            // Merge into existing DRAFT row with same item, don't create duplicate
            Optional<QrOrderItem> existingDraft = orderItemRepository.findByOrderId(order.getOrderId())
                    .stream()
                    .filter(i -> i.getItemId() != null && i.getItemId().equals(mainItemId) && "DRAFT".equals(i.getItemStatus()))
                    .findFirst();

            if (existingDraft.isPresent()) {
                QrOrderItem existing = existingDraft.get();
                existing.setQuantity(existing.getQuantity() + itemDto.getQuantity());
                existing.setSubtotal(existing.getUnitPrice() * existing.getQuantity());
                orderItemRepository.save(existing);
            } else {
                Double foodCost = recipeIngredientRepository.findFoodCostByMenuItemId(menuItem.getId());
                double unitPrice = computePrice(menuItem, foodCost != null ? foodCost : 0.0);
                QrOrderItem orderItem = new QrOrderItem();
                orderItem.setOrderId(order.getOrderId());
                orderItem.setItemId(mainItemId);
                orderItem.setQuantity(itemDto.getQuantity());
                orderItem.setUnitPrice(unitPrice);
                orderItem.setSubtotal(unitPrice * itemDto.getQuantity());
                orderItemRepository.save(orderItem);
            }
        }

        return buildResponse(order, order.getOrderId());
    }

    @Override
    @Transactional(readOnly = true)
    public QrOrderResponse getOrderStatus(Long orderId) {
        QrOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        List<QrOrderItem> items = orderItemRepository.findByOrderId(orderId);

        double totalAmount = items.stream()
                .mapToDouble(QrOrderItem::getSubtotal)
                .sum();

        return buildOrderResponse(order, items, totalAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QrOrderSummaryResponse> getAllOrders() {
        List<QrOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        if (orders.isEmpty()) return List.of();

        List<Long> orderIds = orders.stream()
                .map(QrOrder::getOrderId)
                .collect(Collectors.toList());

        Map<Long, List<QrOrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.groupingBy(QrOrderItem::getOrderId));

        List<Long> tableIds = orders.stream()
                .map(QrOrder::getTableId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> tableNameById = diningTableRepository.findAllById(tableIds)
                .stream()
                .collect(Collectors.toMap(QrDiningTable::getId, t -> t.getTableName() != null ? t.getTableName() : "Bàn " + t.getId()));

        List<Long> reservationIds = orders.stream()
                .map(QrOrder::getReservationId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> guestNameByReservationId = reservationRepository.findAllById(reservationIds)
                .stream()
                .collect(Collectors.toMap(r -> r.getReservationId(), r -> r.getFullName() != null ? r.getFullName() : ""));

        // Fallback: orders without reservationId → lookup by tableId (CONFIRMED or ARRIVED)
        List<Long> tableIdsWithoutReservation = orders.stream()
                .filter(o -> o.getReservationId() == null && o.getTableId() != null)
                .map(QrOrder::getTableId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> guestNameByTableId = tableIdsWithoutReservation.isEmpty()
                ? Collections.emptyMap()
                : reservationRepository.findByTableIdInAndStatusIn(
                        tableIdsWithoutReservation,
                        List.of(ReservationStatus.CONFIRMED, ReservationStatus.ARRIVED))
                  .stream()
                  .collect(Collectors.toMap(
                          r -> r.getTableId(),
                          r -> r.getFullName() != null ? r.getFullName() : "",
                          (a, b) -> a));

        return orders.stream()
                .map(order -> {
                    List<QrOrderItem> items = itemsByOrderId.getOrDefault(order.getOrderId(), List.of());
                    double total = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
                    boolean anyDraft = items.stream().anyMatch(i -> "DRAFT".equals(i.getItemStatus()));
                    boolean anyPreparing = items.stream().anyMatch(i -> "PREPARING".equals(i.getItemStatus()));
                    boolean anyReady = items.stream().anyMatch(i -> "READY".equals(i.getItemStatus()));
                    boolean allDone = !items.isEmpty() && items.stream()
                            .allMatch(i -> "SERVED".equals(i.getItemStatus()) || "CANCELLED".equals(i.getItemStatus()));
                    String serviceStatus = allDone ? "SERVED"
                            : anyDraft ? "HAS_DRAFT"
                            : anyPreparing ? "PREPARING"
                            : anyReady ? "READY"
                            : "OPEN";
                    String tableName = order.getTableId() != null
                            ? tableNameById.getOrDefault(order.getTableId(), "Bàn " + order.getTableId())
                            : null;
                    String guestName = order.getReservationId() != null
                            ? guestNameByReservationId.get(order.getReservationId())
                            : (order.getTableId() != null ? guestNameByTableId.get(order.getTableId()) : null);
                    return new QrOrderSummaryResponse(
                            order.getOrderId(),
                            order.getOrderCode(),
                            order.getTableId(),
                            tableName,
                            guestName,
                            order.getStatus(),
                            serviceStatus,
                            total,
                            order.getCreatedAt(),
                            items.size()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrOrderResponse> getActiveOrder(Long tableId) {
        // QR order takes priority
        Optional<QrOrder> qrOrder = orderRepository.findFirstByTableIdAndStatus(tableId, "OPEN");
        if (qrOrder.isPresent()) {
            return qrOrder.map(order -> {
                List<QrOrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
                double totalAmount = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
                return buildOrderResponse(order, items, totalAmount);
            });
        }

        // Fallback: staff-created (RE) order linked via reservation
        return reservationRepository.findActiveReservationByTableId(tableId)
                .or(() -> reservationRepository.findReservedReservationByTableId(tableId))
                .flatMap(r -> restaurantOrderRepository.findByReservationReservationId(r.getReservationId()))
                .filter(o -> o.getStatus() == OrderStatus.OPEN)
                .map(o -> buildOrderResponseFromRestaurantOrder(o, tableId));
    }

    private QrOrderResponse buildOrderResponseFromRestaurantOrder(RestaurantOrder order, Long tableId) {
        List<QrOrderResponse.OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> {
                    QrOrderResponse.OrderItemDto dto = new QrOrderResponse.OrderItemDto();
                    dto.setOrderItemId(item.getId());
                    dto.setItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null);
                    dto.setItemName(item.getMenuItemName());
                    dto.setItemImageUrl(item.getMenuItemImageUrl());
                    dto.setQuantity(item.getQuantity());
                    dto.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0.0);
                    dto.setSubtotal(item.getSubtotal() != null ? item.getSubtotal().doubleValue() : 0.0);
                    dto.setItemStatus(item.getStatus() != null ? item.getStatus().name() : "DRAFT");
                    dto.setNote(item.getNote());
                    return dto;
                })
                .collect(Collectors.toList());

        double totalAmount = itemDtos.stream().mapToDouble(QrOrderResponse.OrderItemDto::getSubtotal).sum();

        QrOrderResponse response = new QrOrderResponse();
        response.setOrderId(order.getId());
        response.setOrderCode(order.getOrderCode());
        response.setTableId(tableId);
        response.setStatus(order.getStatus().name());
        response.setItems(itemDtos);
        response.setTotalAmount(totalAmount);
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }

    @Override
    @Transactional
    public QrOrderResponse updateItemStatus(Long orderId, Long itemId, String targetStatus) {
        QrOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!"OPEN".equals(order.getStatus())) {
            throw new RuntimeException("Order is not open");
        }

        QrOrderItem item = orderItemRepository.findById(itemId)
                .filter(i -> i.getOrderId().equals(orderId))
                .orElseThrow(() -> new RuntimeException("Order item not found: " + itemId));

        String current = item.getItemStatus();
        boolean valid = ("CONFIRMED".equals(current) && "PREPARING".equals(targetStatus))
                || ("PREPARING".equals(current) && "READY".equals(targetStatus))
                || ("READY".equals(current) && "SERVED".equals(targetStatus));
        if (!valid) {
            throw new RuntimeException("Invalid item status transition: " + current + " → " + targetStatus);
        }

        item.setItemStatus(targetStatus);
        orderItemRepository.save(item);

        List<QrOrderItem> items = orderItemRepository.findByOrderId(orderId);
        double totalAmount = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
        return buildOrderResponse(order, items, totalAmount);
    }

    @Override
    @Transactional
    public QrOrderResponse closeOrder(Long orderId) {
        QrOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!"OPEN".equals(order.getStatus())) {
            throw new RuntimeException("Order is not open");
        }

        List<QrOrderItem> items = orderItemRepository.findByOrderId(orderId);
        boolean allServedOrCancelled = items.stream()
                .allMatch(i -> "SERVED".equals(i.getItemStatus()) || "CANCELLED".equals(i.getItemStatus()));
        boolean hasServed = items.stream().anyMatch(i -> "SERVED".equals(i.getItemStatus()));
        if (!hasServed || !allServedOrCancelled) {
            throw new RuntimeException("All non-cancelled items must be SERVED before closing");
        }

        order.setStatus("CLOSED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        double totalAmount = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
        return buildOrderResponse(order, items, totalAmount);
    }

    @Override
    @Transactional
    public QrOrderResponse addItem(Long orderId, Long menuItemId, Integer quantity) {
        QrOrder order = requireOpenOrder(orderId);
        Long mainItemId = menuModuleItemRepository.getReferenceById(menuItemId).getId();

        // Merge into existing DRAFT row with same item instead of adding a new row
        Optional<QrOrderItem> existingDraft = orderItemRepository.findByOrderId(orderId).stream()
                .filter(i -> i.getItemId().equals(mainItemId) && "DRAFT".equals(i.getItemStatus()))
                .findFirst();

        if (existingDraft.isPresent()) {
            QrOrderItem existing = existingDraft.get();
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setSubtotal(existing.getUnitPrice() * existing.getQuantity());
            orderItemRepository.save(existing);
        } else {
            QrMenuItem qrItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
            Double foodCost = recipeIngredientRepository.findFoodCostByMenuItemId(menuItemId);
            double unitPrice = computePrice(qrItem, foodCost != null ? foodCost : 0.0);

            QrOrderItem item = new QrOrderItem();
            item.setOrderId(orderId);
            item.setItemId(mainItemId);
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(unitPrice * quantity);
            item.setItemStatus("DRAFT");
            orderItemRepository.save(item);
        }

        return buildResponse(order, orderId);
    }

    @Override
    @Transactional
    public QrOrderResponse updateItem(Long orderId, Long itemId, Integer quantity, String note) {
        QrOrder order = requireOpenOrder(orderId);
        QrOrderItem item = requireItem(orderId, itemId);
        if (!"DRAFT".equals(item.getItemStatus()) && !"CONFIRMED".equals(item.getItemStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT or CONFIRMED items can be edited");
        }
        if ("CONFIRMED".equals(item.getItemStatus()) && !quantity.equals(item.getQuantity())) {
            adjustInventory(item, quantity);
        }
        item.setQuantity(quantity);
        item.setSubtotal(item.getUnitPrice() * quantity);
        item.setNote(note != null && !note.isBlank() ? note.strip() : null);
        orderItemRepository.save(item);
        return buildResponse(order, orderId);
    }

    @Override
    @Transactional
    public QrOrderResponse removeItem(Long orderId, Long itemId) {
        QrOrder order = requireOpenOrder(orderId);
        QrOrderItem item = requireItem(orderId, itemId);
        if ("DRAFT".equals(item.getItemStatus())) {
            orderItemRepository.delete(item);
        } else if ("CONFIRMED".equals(item.getItemStatus())) {
            restoreInventory(item);
            item.setItemStatus("CANCELLED");
            orderItemRepository.save(item);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item can no longer be removed");
        }
        return buildResponse(order, orderId);
    }

    @Override
    @Transactional
    public QrOrderResponse submitOrder(Long orderId) {
        QrOrder order = requireOpenOrder(orderId);
        List<QrOrderItem> drafts = orderItemRepository.findByOrderId(orderId).stream()
                .filter(i -> "DRAFT".equals(i.getItemStatus()))
                .toList();
        if (drafts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No draft items to submit");
        }
        for (QrOrderItem item : drafts) {
            submitItem(item);
        }
        reservationRepository.findByTableIdAndStatus(order.getTableId(), ReservationStatus.CONFIRMED)
                .ifPresent(reservation -> {
                    reservation.setStatus(ReservationStatus.ARRIVED);
                    reservationRepository.save(reservation);
                });
        return buildResponse(order, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrAccessTokenResponse> getAccessTokenForTable(Long tableId) {
        return orderService.getOrders(true).stream()
                .filter(o -> tableId.equals(o.tableId()))
                .map(o -> new QrAccessTokenResponse(o.publicAccessToken()))
                .findFirst();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private QrOrder requireOpenOrder(Long orderId) {
        QrOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"OPEN".equals(order.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not open");
        return order;
    }

    private QrOrderItem requireItem(Long orderId, Long itemId) {
        return orderItemRepository.findById(itemId)
                .filter(i -> i.getOrderId().equals(orderId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));
    }

    private QrOrderResponse buildResponse(QrOrder order, Long orderId) {
        List<QrOrderItem> items = orderItemRepository.findByOrderId(orderId);
        double total = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
        return buildOrderResponse(order, items, total);
    }

    private void submitItem(QrOrderItem item) {
        MenuItem menuItem = menuModuleItemRepository.findById(item.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Menu item not found: " + item.getItemId()));
        List<RecipeIngredient> recipe = menuItem.getRecipeIngredients();
        if (recipe.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, menuItem.getName() + " has no recipe");

        for (RecipeIngredient ri : recipe) {
            InventoryItem inv = inventoryRepository.findByIdForUpdate(ri.getInventoryItem().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
            double required = ri.getRequiredQuantity() * item.getQuantity();
            double available = inv.getQuantity() - inv.getReservedQuantity();
            if (!Boolean.TRUE.equals(inv.getIsActive()) || available + EPSILON < required)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough inventory for: " + inv.getItemName());
            inv.setQuantity(inv.getQuantity() - required);
        }
        item.setItemStatus("CONFIRMED");
        item.setSubmittedAt(LocalDateTime.now());
        orderItemRepository.save(item);
    }

    private void restoreInventory(QrOrderItem item) {
        menuModuleItemRepository.findById(item.getItemId()).ifPresent(menuItem ->
                menuItem.getRecipeIngredients().forEach(ri ->
                        inventoryRepository.findByIdForUpdate(ri.getInventoryItem().getId())
                                .ifPresent(inv -> inv.setQuantity(
                                        inv.getQuantity() + ri.getRequiredQuantity() * item.getQuantity()))));
    }

    private void adjustInventory(QrOrderItem item, int newQty) {
        int oldQty = item.getQuantity();
        int delta = newQty - oldQty;
        menuModuleItemRepository.findById(item.getItemId()).ifPresent(menuItem -> {
            for (RecipeIngredient ri : menuItem.getRecipeIngredients()) {
                InventoryItem inv = inventoryRepository.findByIdForUpdate(ri.getInventoryItem().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Inventory item not found"));
                double change = ri.getRequiredQuantity() * Math.abs(delta);
                if (delta > 0) {
                    double available = inv.getQuantity() - inv.getReservedQuantity();
                    if (available + EPSILON < change)
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Not enough inventory for: " + inv.getItemName());
                    inv.setQuantity(inv.getQuantity() - change);
                } else {
                    inv.setQuantity(inv.getQuantity() + change);
                }
            }
        });
    }

    // suggestedPrice = ceil(foodCost * (1 + margin/100) / 1000) * 1000
    private double computePrice(QrMenuItem item, double foodCost) {
        double margin = item.getProfitMarginPercent() == null ? 0.0 : item.getProfitMarginPercent().doubleValue();
        double raw = foodCost * (1.0 + margin / 100.0);
        return Math.ceil(raw / 1000.0) * 1000.0;
    }

    private QrOrderResponse buildOrderResponse(QrOrder order, List<QrOrderItem> items, double totalAmount) {
        List<QrOrderResponse.OrderItemDto> itemDtos = items.stream()
                .map(item -> {
                    QrOrderResponse.OrderItemDto dto = new QrOrderResponse.OrderItemDto();
                    dto.setOrderItemId(item.getOrderItemId());
                    dto.setItemId(item.getItemId());
                    dto.setQuantity(item.getQuantity());
                    dto.setUnitPrice(item.getUnitPrice());
                    dto.setSubtotal(item.getSubtotal());
                    dto.setItemStatus(item.getItemStatus());

                    menuItemRepository.findById(item.getItemId()).ifPresent(mi -> {
                        dto.setItemName(mi.getName());
                        dto.setItemImageUrl(mi.getImageUrl());
                    });
                    dto.setNote(item.getNote());

                    return dto;
                })
                .collect(Collectors.toList());

        QrOrderResponse response = new QrOrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderCode(order.getOrderCode());
        response.setTableId(order.getTableId());
        response.setStatus(order.getStatus());
        response.setItems(itemDtos);
        response.setTotalAmount(totalAmount);
        response.setCreatedAt(order.getCreatedAt());

        return response;
    }
}
