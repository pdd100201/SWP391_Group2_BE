package com.swp391.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.service.MenuService;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.service.OrderService;
import com.swp391.api.modules.payment.service.PaymentService;
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
    @Autowired private PaymentService paymentService;
    @Autowired private MenuService menuService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TableRepository tableRepository;

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
    void submitUsesMenuItemDirectPrice() {
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .filter(item -> item.getPrice() != null)
                .findFirst()
                .orElseThrow();

        RestaurantTable table = tableRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .findFirst()
                .orElseThrow();

        Customer customer = new Customer();
        customer.setCustomersEmail("order-flow-test-" + System.nanoTime() + "@example.com");
        customer.setFullName("Order Flow Test");
        customer.setPassword("test-password");
        customer.setPhone("0900000000");
        customer = customerRepository.save(customer);

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
        assertEquals(dish.getPrice().multiply(java.math.BigDecimal.valueOf(2)).setScale(2), order.items().get(0).lineTotal());

        orderService.removeItem(order.id(), order.items().get(0).id());
        orderService.cancel(order.id());
        assertEquals(
                ReservationStatus.CANCELLED,
                reservationRepository.findById(reservation.getReservationId()).orElseThrow().getStatus());
    }

    @Test
    void createsSepayPaymentWithinTheOrderFlow() {
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .filter(item -> item.getPrice() != null)
                .findFirst()
                .orElseThrow();
        RestaurantTable table = tableRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .findFirst()
                .orElseThrow();

        Customer customer = new Customer();
        customer.setCustomersEmail("sepay-flow-test-" + System.nanoTime() + "@example.com");
        customer.setFullName("SePay Flow Test");
        customer.setPassword("test-password");
        customer.setPhone("0900000001");
        customer = customerRepository.save(customer);

        Reservation reservation = new Reservation();
        reservation.setCustomerId(customer.getCustomerId());
        reservation.setFullName("SePay Flow Test");
        reservation.setPhone("0900000001");
        reservation.setEmail("sepay-flow-test@example.com");
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
        add.setQuantity(1);
        order = orderService.addItem(order.id(), add);
        order = orderService.submit(order.id());
        Long itemId = order.items().get(0).id();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager@goldenspoon.vn",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        order = orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.PREPARING);
        order = orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.READY);
        orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.SERVED);

        OrderResponse paymentOrder = orderService.createSepayPayment(order.id());

        assertEquals("SEPAY", paymentOrder.paymentProvider());
        assertEquals("PENDING", paymentOrder.paymentStatus());
        assertNotNull(paymentOrder.paymentCode());
        assertEquals("PENDING", paymentService.getLatestPayment(order.id()).status());
    }
}
