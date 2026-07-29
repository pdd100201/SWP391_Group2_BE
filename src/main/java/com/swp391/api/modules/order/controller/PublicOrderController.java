package com.swp391.api.modules.order.controller;

import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.order.dto.AddOrderItemRequest;
import com.swp391.api.modules.order.dto.OrderResponse;
import com.swp391.api.modules.order.dto.UpdateOrderItemRequest;
import com.swp391.api.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-access/{token}")
public class PublicOrderController {
    // API gọi món cho khách. Token khó đoán trên mã QR thay cho tài khoản/JWT nhân viên.
    private final OrderService orderService;

    public PublicOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String token) {
        // Cho phép khách tải lại đúng order hiện tại mà không cần đăng nhập.
        return ResponseEntity.ok(orderService.getByToken(token));
    }

    @GetMapping("/menu")
    // Chỉ trả các món đang hoạt động và có trạng thái AVAILABLE/LIMITED.
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable String token) {
        return ResponseEntity.ok(orderService.getPublicMenu(token));
    }

    @PostMapping("/items")
    public ResponseEntity<OrderResponse> addItem(
            @PathVariable String token, @Valid @RequestBody AddOrderItemRequest request) {
        // Món khách thêm được giữ ở DRAFT cho đến khi khách bấm gửi bếp.
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addPublicItem(token, request));
    }

    @PatchMapping("/items/{itemId}")
    // Khách chỉ được sửa món DRAFT thuộc chính order xác định bởi token.
    public ResponseEntity<OrderResponse> updateItem(
            @PathVariable String token,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        return ResponseEntity.ok(orderService.updatePublicItem(token, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    // Khách chỉ xóa được DRAFT; các trạng thái sau phải nhờ nhân viên xử lý.
    public ResponseEntity<OrderResponse> removeItem(@PathVariable String token, @PathVariable Long itemId) {
        return ResponseEntity.ok(orderService.removePublicItem(token, itemId));
    }

    @PostMapping("/submit")
    // Xác nhận toàn bộ món DRAFT trong order của token.
    public ResponseEntity<OrderResponse> submit(@PathVariable String token) {
        return ResponseEntity.ok(orderService.submitPublic(token));
    }
}
