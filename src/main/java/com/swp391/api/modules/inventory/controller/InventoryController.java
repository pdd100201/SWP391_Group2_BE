package com.swp391.api.modules.inventory.controller;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateInventoryItemRequest;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;
import com.swp391.api.modules.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller xử lý các HTTP request liên quan đến quản lý kho nguyên liệu.
 *
 * <p>Base URL: {@code /api/inventory}</p>
 *
 * <p>Phân quyền (được cấu hình trong {@code SecurityConfig}):
 * Chỉ người dùng có role {@code ADMIN} hoặc {@code MANAGER} mới được truy cập.</p>
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}: tự động
 * serialize kết quả trả về thành JSON.</p>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    /** Service xử lý nghiệp vụ kho - inject qua constructor */
    private final InventoryService inventoryService;

    /**
     * Constructor injection để Spring inject {@link InventoryService}.
     * Dùng constructor injection thay vì @Autowired để dễ test.
     *
     * @param inventoryService Service quản lý kho
     */
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Lấy toàn bộ danh sách mặt hàng trong kho.
     * GET /api/inventory
     *
     * @return 200 OK kèm danh sách tất cả mặt hàng
     */
    /** GET /api/inventory */
    @GetMapping
    public ResponseEntity<List<InventoryItemResponse>> getAllItems() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    /**
     * Lấy thông tin chi tiết một mặt hàng theo ID.
     * GET /api/inventory/{id}
     *
     * @param id ID của mặt hàng cần xem
     * @return 200 OK với thông tin mặt hàng, hoặc 404 nếu không tồn tại
     */
    /** GET /api/inventory/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> getItemById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getItemById(id));
    }

    /**
     * Tạo mới một mặt hàng trong kho.
     * POST /api/inventory
     * Body được validate bởi {@code @Valid} - lỗi validate sẽ được xử lý bởi GlobalExceptionHandler.
     *
     * @param request Dữ liệu mặt hàng mới (validated)
     * @return 201 Created kèm thông tin mặt hàng vừa tạo
     */
    /** POST /api/inventory */
    @PostMapping
    public ResponseEntity<InventoryItemResponse> createItem(@Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createItem(request));
    }

    /**
     * Chỉnh sửa các thông tin vận hành của một mặt hàng.
     * PUT /api/inventory/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryService.updateItem(id, request));
    }

    /**
     * Cập nhật số lượng tồn kho của mặt hàng.
     * PUT /api/inventory/{id}/quantity
     * Số lượng mới sẽ thay thế hoàn toàn số lượng cũ.
     *
     * @param id      ID mặt hàng cần cập nhật
     * @param request Số lượng mới (validated)
     * @return 200 OK kèm thông tin mặt hàng sau khi cập nhật
     */
    /** PUT /api/inventory/{id}/quantity */
    @PutMapping("/{id}/quantity")
    public ResponseEntity<InventoryItemResponse> updateQuantity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuantityRequest request) {
        return ResponseEntity.ok(inventoryService.updateQuantity(id, request));
    }

    /**
     * Ghi đè thủ công hoặc reset trạng thái tồn kho.
     * PUT /api/inventory/{id}/status
     *
     * <p>Gửi {@code { "statusOverride": "IN_STOCK" }} để ghi đè thủ công.</p>
     * <p>Gửi {@code { "statusOverride": null }} để reset về tính tự động.</p>
     *
     * Pass { "statusOverride": "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" } to override.
     * Pass { "statusOverride": null } to reset back to auto-calculation.
     *
     * @param id      ID mặt hàng cần cập nhật trạng thái
     * @param request Trạng thái ghi đè (hoặc null)
     * @return 200 OK kèm thông tin mặt hàng sau khi cập nhật
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<InventoryItemResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(inventoryService.updateStatus(id, request));
    }

    /**
     * Bật/tắt trạng thái hoạt động của mặt hàng (soft delete/restore).
     * PATCH /api/inventory/{id}/toggle-active
     * Dùng PATCH vì chỉ thay đổi một phần tài nguyên (trường isActive).
     *
     * @param id ID mặt hàng cần toggle
     * @return 200 OK kèm thông tin mặt hàng sau khi toggle
     */
    /** PATCH /api/inventory/{id}/toggle-active */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<InventoryItemResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.toggleActive(id));
    }

    /**
     * Tìm kiếm mặt hàng với nhiều bộ lọc kết hợp.
     * GET /api/inventory/search?keyword=...&category=...&isActive=...
     * Tất cả tham số đều tùy chọn - có thể dùng một, hai, ba hoặc không có tham số nào.
     *
     * @param keyword  Từ khóa tìm theo tên (tùy chọn)
     * @param category Lọc theo danh mục (tùy chọn)
     * @param isActive Lọc theo trạng thái hoạt động (tùy chọn)
     * @return 200 OK kèm danh sách mặt hàng thỏa điều kiện
     */
    /** GET /api/inventory/search?keyword=...&category=...&isActive=... */
    @GetMapping("/search")
    public ResponseEntity<List<InventoryItemResponse>> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return ResponseEntity.ok(inventoryService.searchItems(keyword, category, isActive));
    }
}
