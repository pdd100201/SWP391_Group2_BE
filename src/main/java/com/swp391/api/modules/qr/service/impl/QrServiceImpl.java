package com.swp391.api.modules.qr.service.impl;

import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.OrderItemResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class QrServiceImpl implements QrService {

    private final QrSessionRepository sessionRepository;
    private final QrMenuItemRepository menuItemRepository;
    private final QrOrderRepository orderRepository;
    private final QrOrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final OrderRepository restaurantOrderRepository;

    public QrServiceImpl(
            QrSessionRepository sessionRepository,
            QrMenuItemRepository menuItemRepository,
            QrOrderRepository orderRepository,
            QrOrderItemRepository orderItemRepository,
            OrderService orderService,
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            OrderRepository restaurantOrderRepository) {
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
    }

    @Override
    @Transactional
    public QrSessionResponse createSession(Long tableId) {
        boolean checkedIn = reservationRepository.findActiveReservationByTableId(tableId).isPresent();
        if (!checkedIn) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TABLE_NOT_CHECKED_IN");
        }

        QrSession session = new QrSession();
        session.setTableId(tableId);
        session.setSessionToken(UUID.randomUUID().toString());
        session.setStartedAt(LocalDateTime.now());
        session.setExpiredAt(LocalDateTime.now().plusHours(24));
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
                                    item.getPrice(),
                                    item.getImageUrl(),
                                    item.getDescription()
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired QR session. Please scan the table QR code again."));

        if (session.getExpiredAt() != null && session.getExpiredAt().isBefore(LocalDateTime.now())) {
            session.setStatus("EXPIRED");
            sessionRepository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR session has expired. Please scan the table QR code again.");
        }

        Long tableId = session.getTableId();
        var reservation = reservationRepository.findActiveReservationByTableId(tableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TABLE_NOT_CHECKED_IN"));
        List<QrMenuItem> menuItems = request.getItems().stream()
                .map(item -> menuItemRepository.findById(item.getItemId())
                        .filter(menuItem -> Boolean.TRUE.equals(menuItem.getIsActive()))
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Menu item is unavailable: " + item.getItemId())))
                .toList();

        QrOrder qrSubmission = new QrOrder();
        qrSubmission.setSessionId(session.getSessionId());
        qrSubmission.setTableId(tableId);
        qrSubmission.setCustomerId(reservation.getCustomerId());
        qrSubmission.setReservationId(reservation.getReservationId());
        qrSubmission.setOrderCode("QRO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        qrSubmission.setOrderType("QR_SUBMISSION");
        qrSubmission.setStatus("PROCESSING");
        qrSubmission.setCreatedAt(LocalDateTime.now());
        qrSubmission.setUpdatedAt(LocalDateTime.now());
        qrSubmission = orderRepository.save(qrSubmission);

        for (int index = 0; index < request.getItems().size(); index++) {
            QrOrderRequest.OrderItemDto requestedItem = request.getItems().get(index);
            QrMenuItem menuItem = menuItems.get(index);
            QrOrderItem auditItem = new QrOrderItem();
            auditItem.setOrderId(qrSubmission.getOrderId());
            auditItem.setItemId(menuItem.getId());
            auditItem.setQuantity(requestedItem.getQuantity());
            auditItem.setUnitPrice(menuItem.getPrice().doubleValue());
            auditItem.setSubtotal(menuItem.getPrice()
                    .multiply(java.math.BigDecimal.valueOf(requestedItem.getQuantity())).doubleValue());
            auditItem.setItemStatus("SUBMITTED");
            auditItem.setNote(requestedItem.getNote());
            auditItem.setSubmittedAt(LocalDateTime.now());
            orderItemRepository.save(auditItem);
        }
        String tokenPreview = request.getSessionToken().length() > 8
                ? request.getSessionToken().substring(0, 8) + "..."
                : request.getSessionToken();
        System.out.println("[QR] createOrder: sessionToken=" + tokenPreview + " tableId=" + tableId);

        // Prefer the staff-created restaurant_order if one is OPEN for this table
        Optional<QrAccessTokenResponse> tokenOpt = getAccessTokenForTable(tableId);
        System.out.println("[QR] createOrder: getAccessTokenForTable result=" + (tokenOpt.isPresent() ? "token present" : "empty"));

        String token;
        if (tokenOpt.isPresent()) {
            token = tokenOpt.get().getPublicAccessToken();
            System.out.println("[QR] createOrder: reusing existing restaurant_order token=" + token.substring(0, 8) + "...");
        } else {
            // No open restaurant_order → create one automatically (admin waiter)
            OrderResponse created = orderService.createForTable(tableId, 1L);
            token = created.publicAccessToken();
            System.out.println("[QR] createOrder: auto-created restaurant_order=" + created.orderCode() + " token=" + token.substring(0, 8) + "...");
        }

        for (QrOrderRequest.OrderItemDto itemDto : request.getItems()) {
            AddOrderItemRequest addReq = new AddOrderItemRequest();
            addReq.setMenuItemId(itemDto.getItemId());
            addReq.setQuantity(itemDto.getQuantity());
            addReq.setNote(itemDto.getNote());
            orderService.addPublicItem(token, addReq);
        }
        OrderResponse submitted = orderService.submitPublic(token);
        qrSubmission.setRestaurantOrderId(submitted.id());
        qrSubmission.setStatus("SYNCED");
        qrSubmission.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(qrSubmission);
        return toQrResponse(submitted, tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public QrOrderResponse getOrderStatus(Long orderId) {
        RestaurantOrder ro = restaurantOrderRepository.findById(orderId).orElse(null);
        if (ro == null) {
            QrOrder submission = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
            if (submission.getRestaurantOrderId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "QR submission has not been synchronized yet");
            }
            ro = restaurantOrderRepository.findById(submission.getRestaurantOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked restaurant order not found"));
        }
        Long tableId = resolveTableId(ro);
        return buildOrderResponseFromRestaurantOrder(ro, tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QrOrderSummaryResponse> getAllOrders() {
        List<QrOrderSummaryResponse> result = new ArrayList<>();

        // Include OPEN restaurant_orders (created via QR auto-flow or by staff)
        List<RestaurantOrder> restaurantOrders = restaurantOrderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(o -> o.getStatus() == OrderStatus.OPEN)
                .collect(Collectors.toList());
        for (RestaurantOrder ro : restaurantOrders) {
            Long tableId = resolveTableId(ro);
            String tableName = tableId != null
                    ? tableRepository.findById(tableId)
                            .map(t -> t.getTableName() != null ? t.getTableName() : "Bàn " + t.getId())
                            .orElse("Bàn " + tableId)
                    : null;
            String guestName = ro.getReservation() != null ? ro.getReservation().getFullName() : null;
            List<QrOrderResponse.OrderItemDto> itemDtos = ro.getItems().stream()
                    .map(item -> {
                        QrOrderResponse.OrderItemDto dto = new QrOrderResponse.OrderItemDto();
                        dto.setOrderItemId(item.getId());
                        dto.setItemStatus(item.getStatus() != null ? item.getStatus().name() : "DRAFT");
                        dto.setSubtotal(item.getSubtotal() != null ? item.getSubtotal().doubleValue() : 0.0);
                        return dto;
                    }).collect(Collectors.toList());
            double total = itemDtos.stream().mapToDouble(QrOrderResponse.OrderItemDto::getSubtotal).sum();
            boolean anyDraft     = itemDtos.stream().anyMatch(i -> "DRAFT".equals(i.getItemStatus()));
            boolean anyPreparing = itemDtos.stream().anyMatch(i -> "PREPARING".equals(i.getItemStatus()));
            boolean anyReady     = itemDtos.stream().anyMatch(i -> "READY".equals(i.getItemStatus()));
            boolean allDone      = !itemDtos.isEmpty() && itemDtos.stream()
                    .allMatch(i -> "SERVED".equals(i.getItemStatus()) || "CANCELLED".equals(i.getItemStatus()));
            String serviceStatus = allDone ? "SERVED" : anyDraft ? "HAS_DRAFT"
                    : anyPreparing ? "PREPARING" : anyReady ? "READY" : "OPEN";
            result.add(new QrOrderSummaryResponse(
                    ro.getId(), ro.getOrderCode(), tableId, tableName, guestName,
                    ro.getStatus().name(), serviceStatus, total, ro.getCreatedAt(), itemDtos.size()));
        }

        List<QrOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        if (orders.isEmpty()) return result;

        List<Long> orderIds = orders.stream().map(QrOrder::getOrderId).collect(Collectors.toList());

        Map<Long, List<QrOrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.groupingBy(QrOrderItem::getOrderId));

        List<Long> tableIds = orders.stream()
                .map(QrOrder::getTableId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> tableNameById = tableRepository.findAllById(tableIds)
                .stream()
                .collect(Collectors.toMap(
                        RestaurantTable::getId,
                        t -> t.getTableName() != null ? t.getTableName() : "Bàn " + t.getId()));

        List<Long> reservationIds = orders.stream()
                .map(QrOrder::getReservationId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> guestNameByReservationId = reservationRepository.findAllById(reservationIds)
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getReservationId(),
                        r -> r.getFullName() != null ? r.getFullName() : ""));

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

        orders.stream()
                .map(order -> {
                    List<QrOrderItem> items = itemsByOrderId.getOrDefault(order.getOrderId(), List.of());
                    double total = items.stream().mapToDouble(QrOrderItem::getSubtotal).sum();
                    boolean anyDraft     = items.stream().anyMatch(i -> "DRAFT".equals(i.getItemStatus()));
                    boolean anyPreparing = items.stream().anyMatch(i -> "PREPARING".equals(i.getItemStatus()));
                    boolean anyReady     = items.stream().anyMatch(i -> "READY".equals(i.getItemStatus()));
                    boolean allDone      = !items.isEmpty() && items.stream()
                            .allMatch(i -> "SERVED".equals(i.getItemStatus()) || "CANCELLED".equals(i.getItemStatus()));
                    String serviceStatus = allDone      ? "SERVED"
                            : anyDraft     ? "HAS_DRAFT"
                            : anyPreparing ? "PREPARING"
                            : anyReady     ? "READY"
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
                .forEach(result::add);
        return result;
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
                .flatMap(r -> restaurantOrderRepository.findByReservationReservationIdAndTableId(r.getReservationId(), tableId))
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
        Optional<QrOrder> qrOrderOpt = orderRepository.findById(orderId);
        if (qrOrderOpt.isPresent()) {
            QrOrder order = qrOrderOpt.get();
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

        // Restaurant order item
        com.swp391.api.modules.order.entity.OrderItemStatus status;
        try {
            status = com.swp391.api.modules.order.entity.OrderItemStatus.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown status: " + targetStatus);
        }
        OrderResponse updated = orderService.updateItemStatus(orderId, itemId, status);
        Long tableId = updated.tableId() != null ? updated.tableId()
                : (updated.tableIds() != null && !updated.tableIds().isEmpty() ? updated.tableIds().get(0) : null);
        return toQrResponse(updated, tableId);
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

        // Merge into existing DRAFT row with same item instead of adding a new row
        Optional<QrOrderItem> existingDraft = orderItemRepository.findByOrderId(orderId).stream()
                .filter(i -> i.getItemId().equals(menuItemId) && "DRAFT".equals(i.getItemStatus()))
                .findFirst();

        if (existingDraft.isPresent()) {
            QrOrderItem existing = existingDraft.get();
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setSubtotal(existing.getUnitPrice() * existing.getQuantity());
            orderItemRepository.save(existing);
        } else {
            QrMenuItem qrItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
            double unitPrice = qrItem.getPrice() == null ? 0.0 : qrItem.getPrice().doubleValue();

            QrOrderItem item = new QrOrderItem();
            item.setOrderId(orderId);
            item.setItemId(menuItemId);
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
            item.setItemStatus("CONFIRMED");
            item.setSubmittedAt(LocalDateTime.now());
            orderItemRepository.save(item);
        }
        // Table is already ARRIVED (enforced by createSession gate); nothing to upgrade.
        return buildResponse(order, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrAccessTokenResponse> getAccessTokenForTable(Long tableId) {
        System.out.println("[QR] getAccessTokenForTable: looking for tableId=" + tableId);
        List<com.swp391.api.modules.order.dto.OrderResponse> allActive = orderService.getOrders(true);
        System.out.println("[QR] getAccessTokenForTable: total active orders=" + allActive.size());
        for (com.swp391.api.modules.order.dto.OrderResponse o : allActive) {
            System.out.println("[QR]   order id=" + o.id()
                    + " tableId=" + o.tableId()
                    + " tableIds=" + o.tableIds()
                    + " publicAccessToken=" + (o.publicAccessToken() != null ? o.publicAccessToken().substring(0, 8) + "..." : "null"));
        }
        Optional<QrAccessTokenResponse> result = allActive.stream()
                .filter(o -> o.publicAccessToken() != null)
                .filter(o -> tableId.equals(o.tableId())
                        || (o.tableIds() != null && o.tableIds().contains(tableId)))
                .map(o -> new QrAccessTokenResponse(o.publicAccessToken()))
                .findFirst();
        System.out.println("[QR] getAccessTokenForTable: result=" + (result.isPresent() ? "token found" : "empty — will use qr_orders fallback"));
        return result;
    }

    private QrOrderResponse toQrResponse(OrderResponse order, Long tableId) {
        List<QrOrderResponse.OrderItemDto> itemDtos = (order.items() == null
                ? List.<OrderItemResponse>of() : order.items())
                .stream()
                .map(item -> {
                    QrOrderResponse.OrderItemDto dto = new QrOrderResponse.OrderItemDto();
                    dto.setOrderItemId(item.id());
                    dto.setItemId(item.menuItemId());
                    dto.setItemName(item.menuItemName());
                    dto.setItemImageUrl(item.menuItemImageUrl());
                    dto.setQuantity(item.quantity());
                    dto.setUnitPrice(item.unitPrice() == null ? 0.0 : item.unitPrice().doubleValue());
                    dto.setSubtotal(item.lineTotal() == null ? 0.0 : item.lineTotal().doubleValue());
                    dto.setItemStatus(item.status() == null ? "CONFIRMED" : item.status().name());
                    dto.setNote(item.note());
                    return dto;
                })
                .collect(Collectors.toList());
        double totalAmount = itemDtos.stream()
                .mapToDouble(QrOrderResponse.OrderItemDto::getSubtotal).sum();
        QrOrderResponse response = new QrOrderResponse();
        response.setOrderId(order.id());
        response.setOrderCode(order.orderCode());
        response.setTableId(tableId);
        response.setStatus(order.status() == null ? "OPEN" : order.status().name());
        response.setItems(itemDtos);
        response.setTotalAmount(totalAmount);
        response.setCreatedAt(order.createdAt());
        return response;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Long resolveTableId(RestaurantOrder ro) {
        if (ro.getReservation() == null) return null;
        if (ro.getReservation().getTableId() != null) return ro.getReservation().getTableId();
        List<?> tables = ro.getReservation().getTables();
        if (tables != null && !tables.isEmpty()) {
            Object first = tables.get(0);
            if (first instanceof com.swp391.api.modules.table.entity.RestaurantTable t) {
                return t.getId();
            }
        }
        return null;
    }

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
                    if (item.getItemId() != null) menuItemRepository.findById(item.getItemId()).ifPresent(mi -> {
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
