package com.swp391.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.repository.OrderItemRepository;
import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderFlowTests {
    @Autowired private OrderService orderService;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private MenuService menuService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private CustomerRepository customerRepository;

    @BeforeEach
    void authenticateWaiter() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "waiter@goldenspoon.vn",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitAndCancelOrderItemFlow() {
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();

        Customer customer = new Customer();
        customer.setCustomersEmail("order-flow-test-" + System.nanoTime() + "@example.com");
        customer.setFullName("Order Flow Test");
        customer.setPassword("test-password");
        customer.setPhone("0900000000");
        customer = customerRepository.save(customer);

        RestaurantTable table = tableRepository.findAvailableActiveTablesOrderByCapacityAsc().stream()
                .filter(candidate -> candidate.getCapacity() >= 2)
                .findFirst()
                .orElseThrow();

        Reservation reservation = new Reservation();
        reservation.setCustomerId(customer.getCustomerId());
        reservation.setFullName("Order Flow Test");
        reservation.setPhone("0900000000");
        reservation.setEmail("order-flow-test@example.com");
        reservation.setReservationDate(LocalDate.now());
        reservation.setReservationTime(LocalTime.now());
        reservation.setNumberOfGuests(2);
        reservation.setStatus(ReservationStatus.ARRIVED);
        reservation.setTableId(table.getId());
        reservation = reservationRepository.save(reservation);

        CreateOrderRequest create = new CreateOrderRequest();
        create.setReservationId(reservation.getReservationId());
        OrderResponse order = orderService.create(create);

        AddOrderItemRequest add = new AddOrderItemRequest();
        add.setMenuItemId(dish.getId());
        add.setQuantity(2);
        order = orderService.addItem(order.id(), add);

        AddOrderItemRequest addSameDraft = new AddOrderItemRequest();
        addSameDraft.setMenuItemId(dish.getId());
        addSameDraft.setQuantity(1);
        order = orderService.addItem(order.id(), addSameDraft);

        assertEquals(1, order.items().size());
        assertEquals(3, order.items().get(0).quantity());

        order = orderService.submit(order.id());
        order = orderService.addItem(order.id(), addSameDraft);

        // Different statuses stay separate until the new draft is submitted.
        assertEquals(2, order.items().size());
        order = orderService.submit(order.id());
        Long orderId = order.id();

        assertEquals(1, order.items().size());
        assertEquals(4, order.items().get(0).quantity());
        assertEquals(OrderItemStatus.CONFIRMED, order.items().get(0).status());
        assertTrue(order.items().get(0).unitPrice().compareTo(dish.getPrice()) == 0);

        // Consolidation is a response concern only: reading must preserve both submitted audit rows.
        assertEquals(2, orderItemRepository.countByOrder_Id(orderId));
        OrderResponse reloaded = orderService.getById(orderId);
        assertEquals(1, reloaded.items().size());
        assertEquals(4, reloaded.items().get(0).quantity());
        assertEquals(2, orderItemRepository.countByOrder_Id(orderId));

        assertTrue(orderService.getOrders(true).stream().anyMatch(active -> active.id().equals(orderId)));
        orderService.removeItem(order.id(), order.items().get(0).id());

        orderService.cancel(order.id());
        assertFalse(orderService.getOrders(true).stream().anyMatch(active -> active.id().equals(orderId)));
        assertTrue(orderService.getOrders(false).stream().anyMatch(history -> history.id().equals(orderId)));
        assertEquals(
                ReservationStatus.CANCELLED,
                reservationRepository.findById(reservation.getReservationId()).orElseThrow().getStatus());
    }

    @Test
    void waiterCanProgressAConsolidatedItemWithoutSplittingItsQuantity() {
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();

        Customer customer = new Customer();
        customer.setCustomersEmail("waiter-status-test-" + System.nanoTime() + "@example.com");
        customer.setFullName("Waiter Status Test");
        customer.setPassword("test-password");
        customer.setPhone("0900000001");
        customer = customerRepository.save(customer);

        RestaurantTable table = tableRepository.findAvailableActiveTablesOrderByCapacityAsc().stream()
                .filter(candidate -> candidate.getCapacity() >= 2)
                .findFirst()
                .orElseThrow();

        Reservation reservation = new Reservation();
        reservation.setCustomerId(customer.getCustomerId());
        reservation.setFullName("Waiter Status Test");
        reservation.setPhone("0900000001");
        reservation.setEmail("waiter-status-test@example.com");
        reservation.setReservationDate(LocalDate.now());
        reservation.setReservationTime(LocalTime.now());
        reservation.setNumberOfGuests(2);
        reservation.setStatus(ReservationStatus.ARRIVED);
        reservation.setTableId(table.getId());
        reservation = reservationRepository.save(reservation);

        CreateOrderRequest create = new CreateOrderRequest();
        create.setReservationId(reservation.getReservationId());
        OrderResponse order = orderService.create(create);

        AddOrderItemRequest firstBatch = new AddOrderItemRequest();
        firstBatch.setMenuItemId(dish.getId());
        firstBatch.setQuantity(2);
        order = orderService.addItem(order.id(), firstBatch);
        order = orderService.submit(order.id());

        AddOrderItemRequest secondBatch = new AddOrderItemRequest();
        secondBatch.setMenuItemId(dish.getId());
        secondBatch.setQuantity(1);
        order = orderService.addItem(order.id(), secondBatch);
        order = orderService.submit(order.id());

        Long displayedItemId = order.items().get(0).id();
        order = orderService.updateItemStatus(order.id(), displayedItemId, OrderItemStatus.PREPARING);
        order = orderService.updateItemStatus(order.id(), displayedItemId, OrderItemStatus.READY);
        order = orderService.updateItemStatus(order.id(), displayedItemId, OrderItemStatus.SERVED);

        assertEquals(1, order.items().size());
        assertEquals(3, order.items().get(0).quantity());
        assertEquals(OrderItemStatus.SERVED, order.items().get(0).status());
        assertEquals(2, orderItemRepository.countByOrder_Id(order.id()));
    }
}
