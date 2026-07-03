package com.swp391.api.modules.order.service;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.entity.RecipeIngredient;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderItemResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemIngredient;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
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
import java.util.Comparator;
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
    // Small tolerance for double inventory calculations.
    private static final double EPSILON = 0.000001;
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER");

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final MenuService menuService;

    public OrderService(
            OrderRepository orderRepository,
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            MenuItemRepository menuItemRepository,
            InventoryRepository inventoryRepository,
            UserRepository userRepository,
            MenuService menuService) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.menuService = menuService;
    }

    public OrderResponse create(CreateOrderRequest request) {
        // Staff opens an order from an arrived/confirmed reservation with an assigned table.
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
        // Staff workspace normally calls this with activeOnly=true to exclude closed orders.
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
        // Public menu is scoped to a valid open order token.
        RestaurantOrder order = orderRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order access link is invalid"));
        requireOpen(order);
        return menuService.getAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .filter(item -> Boolean.TRUE.equals(item.getCostComplete()))
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
        // Items start as DRAFT so staff/guest can still edit before inventory is consumed.
        requireOpen(order);
        MenuItem menuItem = findActiveMenuItem(request.getMenuItemId());

        OrderItem item = new OrderItem();
        item.setMenuItem(menuItem);
        item.setMenuItemName(menuItem.getName());
        item.setMenuItemImageUrl(menuItem.getImageUrl());
        item.setCategoryName(menuItem.getCategory());
        item.setUnitPrice(calculateSuggestedPrice(menuItem));
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
        // Public users can edit only DRAFT items; staff can adjust CONFIRMED quantities.
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
        // Removing confirmed staff items cancels them and restores already deducted inventory.
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
        // QR-table flow: add items to the table's current OPEN order and submit immediately.
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
            item.setUnitPrice(calculateSuggestedPrice(menuItem));
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
        // Submit only draft items; confirmed/preparing/served items are left unchanged.
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
        // Kitchen/service status changes are role-gated and transition-gated.
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
        // Closing is allowed only after every non-cancelled item has been served.
        RestaurantOrder order = findOrderForUpdate(orderId);
        requireOpen(order);
        boolean hasServedItem = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.SERVED);
        boolean hasUnfinishedItem = order.getItems().stream().anyMatch(item ->
                item.getStatus() != OrderItemStatus.SERVED && item.getStatus() != OrderItemStatus.CANCELLED);
        if (!hasServedItem || hasUnfinishedItem) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All non-cancelled items must be served before closing");
        }
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());
        order.getReservation().setStatus(ReservationStatus.COMPLETED);
        updateAssignedTableStatus(order.getReservation(), RestaurantTable.TableStatus.CLEANING);
        return toResponse(orderRepository.save(order));
    }

    public OrderResponse cancel(Long orderId) {
        // Cancellation is blocked once preparation has started.
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
        order.setStatus(OrderStatus.CANCELLED);
        order.setClosedAt(LocalDateTime.now());
        order.getReservation().setStatus(ReservationStatus.CANCELLED);
        updateAssignedTableStatus(order.getReservation(), RestaurantTable.TableStatus.AVAILABLE);
        return toResponse(orderRepository.save(order));
    }

    private void submitItem(OrderItem item) {
        // Confirming an item deducts recipe ingredients and stores an audit snapshot.
        MenuItem menuItem = findActiveMenuItem(item.getMenuItem().getId());
        List<RecipeIngredient> recipe = menuItem.getRecipeIngredients().stream()
                .sorted(Comparator.comparing(ingredient -> ingredient.getInventoryItem().getId()))
                .toList();
        if (recipe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, menuItem.getName() + " has no recipe");
        }

        BigDecimal foodCost = BigDecimal.ZERO;
        for (RecipeIngredient recipeIngredient : recipe) {
            // Lock inventory rows in deterministic order to reduce deadlock risk.
            InventoryItem inventory = lockInventory(recipeIngredient.getInventoryItem().getId());
            double required = recipeIngredient.getRequiredQuantity() * item.getQuantity();
            double available = inventory.getQuantity() - inventory.getReservedQuantity();
            if (!Boolean.TRUE.equals(inventory.getIsActive()) || available + EPSILON < required) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough available inventory for " + inventory.getItemName());
            }
            if (inventory.getPricePerUnit() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Inventory price is missing for " + inventory.getItemName());
            }
            inventory.setQuantity(inventory.getQuantity() - required);
            foodCost = foodCost.add(BigDecimal.valueOf(inventory.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(recipeIngredient.getRequiredQuantity())));

            OrderItemIngredient snapshot = new OrderItemIngredient();
            snapshot.setInventoryItem(inventory);
            snapshot.setInventoryItemName(inventory.getItemName());
            snapshot.setUnit(inventory.getUnit());
            snapshot.setDeductedQuantity(required);
            item.addIngredient(snapshot);
        }

        item.setMenuItemName(menuItem.getName());
        item.setMenuItemImageUrl(menuItem.getImageUrl());
        item.setCategoryName(menuItem.getCategory());
        item.setUnitPrice(applyMargin(foodCost, menuItem.getProfitMarginPercent()));
        updateSubtotal(item);
        item.setStatus(OrderItemStatus.CONFIRMED);
        item.setSubmittedAt(LocalDateTime.now());
    }

    private void adjustConfirmedQuantity(OrderItem item, int newQuantity) {
        // Quantity changes after confirmation adjust the previously saved ingredient snapshot.
        int oldQuantity = item.getQuantity();
        if (newQuantity == oldQuantity) return;
        int delta = newQuantity - oldQuantity;
        for (OrderItemIngredient snapshot : item.getIngredients()) {
            InventoryItem inventory = lockInventory(snapshot.getInventoryItem().getId());
            double perServing = snapshot.getDeductedQuantity() / oldQuantity;
            double change = perServing * Math.abs(delta);
            if (delta > 0) {
                double available = inventory.getQuantity() - inventory.getReservedQuantity();
                if (available + EPSILON < change) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Not enough available inventory for " + inventory.getItemName());
                }
                inventory.setQuantity(inventory.getQuantity() - change);
                snapshot.setDeductedQuantity(snapshot.getDeductedQuantity() + change);
            } else {
                inventory.setQuantity(inventory.getQuantity() + change);
                snapshot.setDeductedQuantity(Math.max(0.0, snapshot.getDeductedQuantity() - change));
            }
        }
        item.setQuantity(newQuantity);
        updateSubtotal(item);
    }

    private void restoreInventory(OrderItem item) {
        for (OrderItemIngredient snapshot : item.getIngredients().stream()
                .sorted(Comparator.comparing(ingredient -> ingredient.getInventoryItem().getId()))
                .toList()) {
            InventoryItem inventory = lockInventory(snapshot.getInventoryItem().getId());
            inventory.setQuantity(inventory.getQuantity() + snapshot.getDeductedQuantity());
        }
    }

    private BigDecimal calculateSuggestedPrice(MenuItem item) {
        BigDecimal cost = BigDecimal.ZERO;
        if (item.getRecipeIngredients().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish has no recipe");
        }
        for (RecipeIngredient ingredient : item.getRecipeIngredients()) {
            Double price = ingredient.getInventoryItem().getPricePerUnit();
            if (price == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish price is not available");
            }
            cost = cost.add(BigDecimal.valueOf(price)
                    .multiply(BigDecimal.valueOf(ingredient.getRequiredQuantity())));
        }
        return applyMargin(cost, item.getProfitMarginPercent());
    }

    private BigDecimal applyMargin(BigDecimal cost, Double marginPercent) {
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(marginPercent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        BigDecimal raw = cost.multiply(multiplier);
        return raw.divide(BigDecimal.valueOf(1000), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(1000)).setScale(2);
    }

    private OrderResponse toResponse(RestaurantOrder order) {
        // Single response shape for staff and public clients.
        List<OrderItemResponse> items = order.getItems().stream().map(this::toItemResponse).toList();
        BigDecimal total = items.stream()
                .filter(item -> item.status() != OrderItemStatus.CANCELLED)
                .map(OrderItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Reservation reservation = order.getReservation();
        RestaurantTable table = findAssignedTable(reservation);
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                reservation.getReservationId(),
                reservation.getFullName(),
                reservation.getStatus(),
                reservation.getTableId(),
                table == null ? null : table.getTableNumber(),
                table == null ? null : table.getTableName(),
                table == null || table.getStatus() == null ? null : table.getStatus().name(),
                order.getWaiter().getUserId(),
                order.getWaiter().getFullName(),
                order.getPublicAccessToken(),
                "/order-access/" + order.getPublicAccessToken(),
                order.getStatus(),
                serviceStatus(order),
                order.getNote(),
                total,
                items,
                order.getClosedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private RestaurantTable findAssignedTable(Reservation reservation) {
        Long tableId = resolvePrimaryTableId(reservation);
        if (tableId == null) return null;
        return tableRepository.findById(tableId).orElse(null);
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
        // Derived status summarizes item state for the order list UI.
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

    private InventoryItem lockInventory(Long id) {
        return inventoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Inventory item no longer exists"));
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
}
