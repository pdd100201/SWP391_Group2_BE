package com.swp391.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.entity.OrderItemStatus;
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
    @Autowired private MenuService menuService;
    @Autowired private InventoryRepository inventoryRepository;
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
    void submitDeductsInventoryAndCancellingConfirmedItemRestoresIt() {
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();
        InventoryItem lettuce = inventoryRepository.findByItemNameIgnoreCase("Lettuce").orElseThrow();
        Long inventoryId = lettuce.getId();
        double quantityBefore = lettuce.getQuantity();

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
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTableId(table.getId());
        reservation = reservationRepository.save(reservation);

        CreateOrderRequest create = new CreateOrderRequest();
        create.setReservationId(reservation.getReservationId());
        OrderResponse order = orderService.create(create);

        AddOrderItemRequest add = new AddOrderItemRequest();
        add.setMenuItemId(dish.getId());
        add.setQuantity(2);
        order = orderService.addItem(order.id(), add);
        order = orderService.submit(order.id());

        assertEquals(OrderItemStatus.CONFIRMED, order.items().get(0).status());
        assertTrue(order.items().get(0).unitPrice().compareTo(java.math.BigDecimal.valueOf(dish.getPrice())) == 0);
        double expectedDeduction = 0.15 * 2;
        InventoryItem afterSubmit = inventoryRepository.findById(inventoryId).orElseThrow();
        assertEquals(quantityBefore - expectedDeduction, afterSubmit.getQuantity(), 0.000001);

        orderService.removeItem(order.id(), order.items().get(0).id());
        InventoryItem afterCancel = inventoryRepository.findById(inventoryId).orElseThrow();
        assertEquals(quantityBefore, afterCancel.getQuantity(), 0.000001);

        orderService.cancel(order.id());
        assertEquals(
                ReservationStatus.CANCELLED,
                reservationRepository.findById(reservation.getReservationId()).orElseThrow().getStatus());
    }
}
