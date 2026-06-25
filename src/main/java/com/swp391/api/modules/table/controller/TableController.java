package com.swp391.api.modules.table.controller;

import com.swp391.api.modules.table.dto.TableRequest;
import com.swp391.api.modules.table.dto.TableResponse;
import com.swp391.api.modules.table.dto.UpdateTableStatusRequest;
import com.swp391.api.modules.table.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller xử lý các HTTP request liên quan đến quản lý bàn nhà hàng.
 *
 * <p>Base URL: {@code /api/tables}</p>
 *
 * <p>Phân quyền (được cấu hình trong {@code SecurityConfig}):
 * Chỉ người dùng có role {@code ADMIN} hoặc {@code MANAGER} mới được truy cập
 * (ngoại trừ một số endpoint public nếu cần).</p>
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}: tự động
 * serialize kết quả trả về thành JSON.</p>
 */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    /** Service xử lý nghiệp vụ bàn - inject qua constructor */
    private final TableService tableService;

    /**
     * Constructor injection để Spring inject {@link TableService}.
     * Dùng constructor injection thay vì @Autowired để dễ test.
     *
     * @param tableService Service quản lý bàn
     */
    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    /**
     * Lấy toàn bộ danh sách bàn.
     * GET /api/tables
     *
     * @return 200 OK kèm danh sách tất cả bàn
     */
    @GetMapping
    public ResponseEntity<List<TableResponse>> getAllTables() {
        return ResponseEntity.ok(tableService.getAllTables());
    }

    /**
     * Lấy danh sách bàn đang hoạt động (active = true).
     * GET /api/tables/active
     *
     * @return 200 OK kèm danh sách bàn đang hoạt động
     */
    @GetMapping("/active")
    public ResponseEntity<List<TableResponse>> getActiveTables() {
        return ResponseEntity.ok(tableService.getActiveTables());
    }

    /**
     * Lấy thông tin chi tiết một bàn theo ID.
     * GET /api/tables/{id}
     *
     * @param id ID của bàn cần xem
     * @return 200 OK với thông tin bàn, hoặc 404 nếu không tồn tại
     */
    @GetMapping("/{id}")
    public ResponseEntity<TableResponse> getTableById(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.getTableById(id));
    }

    /**
     * Lấy danh sách bàn theo loại bàn.
     * GET /api/tables/by-type/{tableType}
     *
     * @param tableType Loại bàn (Main Hall, VIP Room, Patio...)
     * @return 200 OK kèm danh sách bàn có loại đó
     */
    @GetMapping("/by-type/{tableType}")
    public ResponseEntity<List<TableResponse>> getTablesByType(@PathVariable String tableType) {
        return ResponseEntity.ok(tableService.getTablesByType(tableType));
    }

    /**
     * Lấy danh sách bàn theo trạng thái.
     * GET /api/tables/by-status/{status}
     *
     * @param status Trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED, CLEANING)
     * @return 200 OK kèm danh sách bàn có trạng thái đó
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<TableResponse>> getTablesByStatus(@PathVariable String status) {
        return ResponseEntity.ok(tableService.getTablesByStatus(status));
    }

    /**
     * Tạo bàn mới.
     * POST /api/tables
     * Body được validate bởi {@code @Valid} - lỗi validate sẽ được xử lý bởi GlobalExceptionHandler.
     *
     * @param request Dữ liệu bàn mới (validated)
     * @return 201 Created kèm thông tin bàn vừa tạo
     */
    @PostMapping
    public ResponseEntity<TableResponse> createTable(@Valid @RequestBody TableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTable(request));
    }

    /**
     * Cập nhật thông tin bàn (tên, loại, sức chứa, QR code).
     * PUT /api/tables/{id}
     *
     * @param id      ID bàn cần cập nhật
     * @param request Thông tin bàn mới (validated)
     * @return 200 OK kèm thông tin bàn sau cập nhật
     */
    @PutMapping("/{id}")
    public ResponseEntity<TableResponse> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody TableRequest request) {
        return ResponseEntity.ok(tableService.updateTable(id, request));
    }

    /**
     * Cập nhật trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED, CLEANING).
     * PATCH /api/tables/{id}/status
     *
     * @param id      ID bàn cần cập nhật
     * @param request Trạng thái mới
     * @return 200 OK kèm thông tin bàn sau cập nhật
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TableResponse> updateTableStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableStatusRequest request) {
        return ResponseEntity.ok(tableService.updateTableStatus(id, request));
    }

    /**
     * Bật/tắt trạng thái hoạt động của bàn (soft delete/restore).
     * PATCH /api/tables/{id}/toggle-active
     *
     * @param id ID bàn cần toggle
     * @return 200 OK kèm thông tin bàn sau toggle
     */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<TableResponse> toggleTableActive(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.toggleTableActive(id));
    }

    /**
     * Xóa bàn khỏi hệ thống (hard delete).
     * DELETE /api/tables/{id}
     *
     * @param id ID bàn cần xóa
     * @return 204 No Content nếu xóa thành công
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTable(@PathVariable Long id) {
        tableService.deleteTable(id);
        return ResponseEntity.noContent().build();
    }
}
