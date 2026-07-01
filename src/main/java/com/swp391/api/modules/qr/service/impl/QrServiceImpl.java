package com.swp391.api.modules.qr.service.impl;

import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.qr.dto.*;
import com.swp391.api.modules.qr.entity.*;
import com.swp391.api.modules.qr.repository.*;
import com.swp391.api.modules.qr.service.QrService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QrServiceImpl implements QrService {

    private final QrSessionRepository sessionRepository;
    private final QrMenuItemRepository menuItemRepository;
    private final QrRecipeIngredientRepository recipeIngredientRepository;
    private final MenuItemRepository menuModuleItemRepository;
    private final QrOrderRepository orderRepository;
    private final QrOrderItemRepository orderItemRepository;

    public QrServiceImpl(
            QrSessionRepository sessionRepository,
            QrMenuItemRepository menuItemRepository,
            QrRecipeIngredientRepository recipeIngredientRepository,
            MenuItemRepository menuModuleItemRepository,
            QrOrderRepository orderRepository,
            QrOrderItemRepository orderItemRepository) {
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.menuModuleItemRepository = menuModuleItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional
    public QrSessionResponse createSession(Long tableId) {
        QrSession session = new QrSession();
        session.setTableId(tableId);
        session.setSessionToken(UUID.randomUUID().toString());
        session.setStartedAt(LocalDateTime.now());
        session.setExpiredAt(LocalDateTime.now().plusHours(2));
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

        if (session.getExpiredAt().isBefore(LocalDateTime.now())) {
            session.setStatus("EXPIRED");
            sessionRepository.save(session);
            throw new RuntimeException("Session has expired");
        }

        QrOrder order = new QrOrder();
        order.setTableId(session.getTableId());
        order.setOrderType("QR_ORDER");
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        List<QrOrderItem> savedItems = new ArrayList<>();
        double totalAmount = 0.0;

        for (QrOrderRequest.OrderItemDto itemDto : request.getItems()) {
            QrMenuItem menuItem = menuItemRepository.findById(itemDto.getItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemDto.getItemId()));

            Double foodCost = recipeIngredientRepository.findFoodCostByMenuItemId(menuItem.getId());
            double unitPrice = computePrice(menuItem, foodCost != null ? foodCost : 0.0);
            double subtotal = unitPrice * itemDto.getQuantity();

            QrOrderItem orderItem = new QrOrderItem();
            orderItem.setOrderId(order.getOrderId());
            orderItem.setItemId(menuModuleItemRepository.getReferenceById(menuItem.getId()).getId());
            orderItem.setQuantity(itemDto.getQuantity());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setSubtotal(subtotal);
            orderItem.setItemStatus("PENDING");

            savedItems.add(orderItemRepository.save(orderItem));
            totalAmount += subtotal;
        }

        return buildOrderResponse(order, savedItems, totalAmount);
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

                    menuItemRepository.findById(item.getItemId())
                            .ifPresent(mi -> dto.setItemName(mi.getName()));

                    return dto;
                })
                .collect(Collectors.toList());

        QrOrderResponse response = new QrOrderResponse();
        response.setOrderId(order.getOrderId());
        response.setTableId(order.getTableId());
        response.setStatus(order.getStatus());
        response.setItems(itemDtos);
        response.setTotalAmount(totalAmount);
        response.setCreatedAt(order.getCreatedAt());

        return response;
    }
}
