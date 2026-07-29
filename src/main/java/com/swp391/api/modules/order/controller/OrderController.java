package com.swp391.api.modules.order.controller;

import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.ApplyPromotionRequest;
import com.swp391.api.modules.order.dto.CreateOrderRequest;
import com.swp391.api.modules.order.dto.OrderGroupResponse;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.dto.UpdateOrderItemStatusRequest;
import com.swp391.api.modules.order.dto.VoidOrderItemRequest;
import com.swp391.api.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
// API nội bộ cho nhân viên. Quyền ở cấp lớp bảo vệ toàn bộ endpoint Order Management.
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    // Tạo/đồng bộ một order cho từng bàn đã gán vào reservation đã check-in.
    public ResponseEntity<OrderGroupResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createTableOrders(request));
    }

    @GetMapping
    // Trả danh sách phẳng; active=true chỉ giữ order còn phục vụ/chưa thanh toán.
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(orderService.getOrders(activeOnly));
    }

    @GetMapping("/groups")
    // Gom các order bàn theo reservation và gắn hóa đơn dùng chung của nhóm.
    public ResponseEntity<List<OrderGroupResponse>> getGroups(
            @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(orderService.getGroups(activeOnly));
    }

    @GetMapping("/groups/{reservationId}")
    // Lấy lại đúng một nhóm sau khi frontend vừa thay đổi món hoặc trạng thái.
    public ResponseEntity<OrderGroupResponse> getGroup(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.getGroup(reservationId));
    }

    @GetMapping("/{orderId}")
    // Lấy chi tiết một order theo khóa chính.
    public ResponseEntity<OrderResponse> getById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getById(orderId));
    }

    @GetMapping("/by-reservation/{reservationId}")
    // Trả order đại diện mới nhất của reservation cho các luồng tương thích cũ.
    public ResponseEntity<OrderResponse> getByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.getByReservation(reservationId));
    }

    @PostMapping("/{orderId}/items")
    // @Valid kiểm tra quantity, menuItemId và độ dài note trước khi vào service.
    public ResponseEntity<OrderResponse> addItem(
            @PathVariable Long orderId, @Valid @RequestBody AddOrderItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addItem(orderId, request));
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    // Cập nhật số lượng/ghi chú; service quyết định trạng thái nào còn được sửa.
    public ResponseEntity<OrderResponse> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        return ResponseEntity.ok(orderService.updateItem(orderId, itemId, request));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    // DRAFT bị xóa vật lý; CONFIRMED đổi sang CANCELLED để giữ lịch sử.
    public ResponseEntity<OrderResponse> removeItem(@PathVariable Long orderId, @PathVariable Long itemId) {
        return ResponseEntity.ok(orderService.removeItem(orderId, itemId));
    }

    @PostMapping("/{orderId}/items/{itemId}/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<OrderResponse> voidServedItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody VoidOrderItemRequest request) {
        return ResponseEntity.ok(orderService.voidServedItem(orderId, itemId, request));
    }

    @PostMapping("/{orderId}/submit")
    // Chuyển toàn bộ món DRAFT sang CONFIRMED để bếp tiếp nhận.
    public ResponseEntity<OrderResponse> submit(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.submit(orderId));
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    // Chuyển món tuần tự qua PREPARING, READY và SERVED.
    public ResponseEntity<OrderResponse> updateItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemStatusRequest request) {
        return ResponseEntity.ok(orderService.updateItemStatus(orderId, itemId, request.getStatus()));
    }

    @PostMapping("/{orderId}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    // Endpoint tương thích cũ: ủy quyền PaymentService tạo giao dịch SePay.
    public ResponseEntity<OrderResponse> createSepayPayment(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createSepayPayment(orderId));
    }

    @PostMapping("/{orderId}/payment/cash")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RECEPTIONIST')")
    // Endpoint tương thích cũ: ghi nhận thanh toán tiền mặt.
    public ResponseEntity<OrderResponse> createCashPayment(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createCashPayment(orderId));
    }

    @PatchMapping("/{orderId}/close")
    // Đóng order đơn lẻ sau khi mọi món đã phục vụ và order đã thanh toán.
    public ResponseEntity<OrderResponse> close(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.close(orderId));
    }

    @PatchMapping("/reservations/{reservationId}/complete")
    // Hoàn tất đồng thời mọi order bàn trong reservation dùng chung bill.
    public ResponseEntity<OrderGroupResponse> completeReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(orderService.completeReservation(reservationId));
    }

    @PatchMapping("/{orderId}/promotion")
    // Endpoint tương thích cũ để áp mã giảm giá trực tiếp qua order.
    public ResponseEntity<OrderResponse> applyPromotion(
            @PathVariable Long orderId,
            @Valid @RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(orderService.applyPromotion(orderId, request.getCode()));
    }

    @DeleteMapping("/{orderId}/promotion")
    // Xóa khuyến mãi và hoàn lại một lượt sử dụng nếu đã ghi nhận.
    public ResponseEntity<OrderResponse> removePromotion(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.removePromotion(orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    // Chỉ hủy khi chưa có món bắt đầu chế biến.
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }
}
