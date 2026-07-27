package com.swp391.api.modules.menu.controller;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cung cấp các API quản lý món ăn tại đường dẫn /api/menu.
 * Controller chỉ tiếp nhận request, kiểm tra dữ liệu đầu vào và chuyển công việc
 * cho MenuService; toàn bộ quy tắc nghiệp vụ được xử lý ở tầng service.
 */
@RestController
@RequestMapping("/api/menu")
public class MenuController {
    private final MenuService menuService;

    // Spring tự truyền MenuService vào constructor khi khởi tạo controller.
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    // Lấy toàn bộ món để hiển thị trên màn hình quản lý Menu.
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAll() {
        return ResponseEntity.ok(menuService.getAll());
    }

    // Lấy chi tiết một món theo ID; service sẽ trả lỗi 404 nếu ID không tồn tại.
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getById(id));
    }

    // Tạo món mới. @Valid kích hoạt các quy tắc kiểm tra trong MenuItemRequest.
    // Chỉ ADMIN hoặc MANAGER được phép thực hiện thao tác làm thay đổi dữ liệu này.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.create(request));
    }

    // Cập nhật toàn bộ thông tin của món có ID trên URL.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(menuService.update(id, request));
    }

    // Bật/ngừng phục vụ món mà không xóa bản ghi, nhờ đó lịch sử đơn hàng vẫn được giữ nguyên.
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.toggleActive(id));
    }

}
