package com.swp391.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
// Kiểm thử tích hợp Order với database thật trong transaction; dữ liệu được rollback sau mỗi test.
class OrderFlowTests {
    @Autowired private OrderService orderService;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private MenuService menuService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private CustomerRepository customerRepository;

    @BeforeEach
    // Giả lập người dùng Waiter đã đăng nhập để các kiểm tra quyền trong service hoạt động.
    void authenticateWaiter() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "waiter@goldenspoon.vn",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
    }

    @AfterEach
    // Xóa SecurityContext để danh tính test này không rò sang test khác.
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    // Bao phủ luồng: tạo order -> gộp DRAFT -> submit -> giữ audit -> hủy món/order.
    void submitAndCancelOrderItemFlow() {
        // Dùng món seed có sẵn để kiểm tra đúng giá snapshot khi submit.
        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();

        // Tạo customer, bàn và reservation ARRIVED làm tiền điều kiện mở order.
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

        // Tạo order và thêm hai lần cùng món; service phải gộp thành một DRAFT quantity = 3.
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

        // Hai trạng thái khác nhau tạm thời hiển thị thành hai dòng cho đến khi DRAFT mới được submit.
        assertEquals(2, order.items().size());
        order = orderService.submit(order.id());
        Long orderId = order.id();

        assertEquals(1, order.items().size());
        assertEquals(4, order.items().get(0).quantity());
        assertEquals(OrderItemStatus.CONFIRMED, order.items().get(0).status());
        assertTrue(order.items().get(0).unitPrice().compareTo(dish.getPrice()) == 0);

        // Việc gộp chỉ xảy ra ở response; DB vẫn giữ hai dòng audit đã submit.
        assertEquals(2, orderItemRepository.countByOrder_Id(orderId));
        OrderResponse reloaded = orderService.getById(orderId);
        assertEquals(1, reloaded.items().size());
        assertEquals(4, reloaded.items().get(0).quantity());
        assertEquals(2, orderItemRepository.countByOrder_Id(orderId));

        assertTrue(orderService.getOrders(true).stream().anyMatch(active -> active.id().equals(orderId)));
        orderService.removeItem(order.id(), order.items().get(0).id());

        // Order bị hủy biến mất khỏi active nhưng vẫn tồn tại trong lịch sử đầy đủ.
        orderService.cancel(order.id());
        assertFalse(orderService.getOrders(true).stream().anyMatch(active -> active.id().equals(orderId)));
        assertTrue(orderService.getOrders(false).stream().anyMatch(history -> history.id().equals(orderId)));
        assertEquals(
                ReservationStatus.CANCELLED,
                reservationRepository.findById(reservation.getReservationId()).orElseThrow().getStatus());
    }

    @Test
    // Bao phủ luồng bếp hoàn chỉnh trước khi tạo giao dịch SePay ở trạng thái PENDING.
    void createsSepayPaymentFromOrderAfterItemsAreServed() {
        // Manager là một trong các role được chuyển trạng thái món.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager@goldenspoon.vn",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));

        MenuItemResponse dish = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();

        Customer customer = new Customer();
        customer.setCustomersEmail("sepay-order-test-" + System.nanoTime() + "@example.com");
        customer.setFullName("SePay Order Test");
        customer.setPassword("test-password");
        customer.setPhone("0900000001");
        customer = customerRepository.save(customer);

        RestaurantTable table = tableRepository.findAvailableActiveTablesOrderByCapacityAsc().stream()
                .filter(candidate -> candidate.getCapacity() >= 2)
                .findFirst()
                .orElseThrow();

        Reservation reservation = new Reservation();
        reservation.setCustomerId(customer.getCustomerId());
        reservation.setFullName("SePay Order Test");
        reservation.setPhone("0900000001");
        reservation.setEmail("sepay-order-test@example.com");
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
        add.setQuantity(1);
        order = orderService.addItem(order.id(), add);
        order = orderService.submit(order.id());

        // Bắt buộc chuyển tuần tự CONFIRMED -> PREPARING -> READY -> SERVED.
        Long itemId = order.items().get(0).id();
        order = orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.PREPARING);
        order = orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.READY);
        order = orderService.updateItemStatus(order.id(), itemId, OrderItemStatus.SERVED);
        order = orderService.createSepayPayment(order.id());

        // Tạo SePay chỉ khởi tạo giao dịch PENDING; webhook thanh toán sẽ xác nhận PAID sau.
        assertEquals("SEPAY", order.paymentProvider());
        assertEquals("PENDING", order.paymentStatus());
        assertNotNull(order.paymentCode());
    }
}
