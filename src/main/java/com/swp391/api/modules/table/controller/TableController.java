package com.swp391.api.modules.table.controller;

import com.swp391.api.modules.table.dto.TableRequest;
import com.swp391.api.modules.table.dto.TableResponse;
import com.swp391.api.modules.table.dto.UpdateTableStatusRequest;
import com.swp391.api.modules.table.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    /**
     * Lấy TOÀN BỘ danh sách bàn (Dành cho màn hình Quản lý của Admin).
     * ĐÃ TRẢ VỀ NGUYÊN BẢN: Hiện cả bàn active lẫn deactive để Admin còn thấy đường bật/tắt.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<List<TableResponse>> getAllTables() {
        return ResponseEntity.ok(tableService.getAllTables());
    }

    /**
     * Lấy danh sách bàn đang hoạt động (active = true).
     * LƯU Ý: Màn hình Check-in phải gọi vào đường dẫn này (/api/tables/active) để ẩn bàn deactive.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<List<TableResponse>> getActiveTables() {
        return ResponseEntity.ok(tableService.getActiveTables());
    }

    @GetMapping("/status-now")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<List<TableResponse>> getTablesStatusNow() {
        return ResponseEntity.ok(tableService.getTablesStatusNow());
    }

    /**
     * Lấy thông tin chi tiết một bàn theo ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<TableResponse> getTableById(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.getTableById(id));
    }

    /**
     * Lấy danh sách bàn theo loại bàn.
     */
    @GetMapping("/by-type/{tableType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<List<TableResponse>> getTablesByType(@PathVariable String tableType) {
        return ResponseEntity.ok(tableService.getTablesByType(tableType));
    }

    /**
     * Lấy danh sách bàn theo trạng thái.
     */
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<List<TableResponse>> getTablesByStatus(@PathVariable String status) {
        return ResponseEntity.ok(tableService.getTablesByStatus(status));
    }

    /**
     * Tạo bàn mới.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TableResponse> createTable(@Valid @RequestBody TableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTable(request));
    }

    /**
     * Cập nhật thông tin bàn.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TableResponse> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody TableRequest request) {
        return ResponseEntity.ok(tableService.updateTable(id, request));
    }

    /**
     * Cập nhật trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED, CLEANING).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'RECEPTIONIST')")
    public ResponseEntity<TableResponse> updateTableStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableStatusRequest request) {
        return ResponseEntity.ok(tableService.updateTableStatus(id, request));
    }

    /**
     * Bật/tắt trạng thái hoạt động của bàn.
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TableResponse> toggleTableActive(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.toggleTableActive(id));
    }

    /**
     * Xóa bàn khỏi hệ thống.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteTable(@PathVariable Long id) {
        tableService.deleteTable(id);
        return ResponseEntity.noContent().build();
    }
}
