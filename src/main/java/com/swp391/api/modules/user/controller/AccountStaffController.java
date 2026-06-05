package com.swp391.api.modules.user.controller;

import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StaffRequest;
import com.swp391.api.modules.user.dto.StaffResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.service.AccountStaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/accounts/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AccountStaffController {

    private final AccountStaffService accountStaffService;

    public AccountStaffController(AccountStaffService accountStaffService) {
        this.accountStaffService = accountStaffService;
    }

    /**
     * GET /api/admin/accounts/staff
     * Lấy danh sách staff có phân trang, tìm kiếm, lọc.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStaffList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<StaffResponse> data = accountStaffService.getStaffList(keyword, role, status, page, size);
        return ResponseEntity.ok(Map.of(
                "message", "Staff list retrieved successfully",
                "data", data
        ));
    }

    /**
     * GET /api/admin/accounts/staff/{id}
     * Xem chi tiết staff.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getStaffById(@PathVariable Long id) {
        StaffResponse data = accountStaffService.getStaffById(id);
        return ResponseEntity.ok(Map.of(
                "message", "Staff retrieved successfully",
                "data", data
        ));
    }

    /**
     * POST /api/admin/accounts/staff
     * Tạo staff mới.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createStaff(@Valid @RequestBody StaffRequest request) {
        StaffResponse data = accountStaffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Staff created successfully",
                "data", data
        ));
    }

    /**
     * PUT /api/admin/accounts/staff/{id}
     * Sửa thông tin staff.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffRequest request) {
        StaffResponse data = accountStaffService.updateStaff(id, request);
        return ResponseEntity.ok(Map.of(
                "message", "Staff updated successfully",
                "data", data
        ));
    }

    /**
     * PATCH /api/admin/accounts/staff/{id}/status
     * Bật/tắt trạng thái ACTIVE/DEACTIVE.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStaffStatus(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest request) {
        StaffResponse data = accountStaffService.updateStaffStatus(id,
                request != null ? request : new StatusUpdateRequest());
        return ResponseEntity.ok(Map.of(
                "message", "Staff status updated successfully",
                "data", data
        ));
    }

    /**
     * DELETE /api/admin/accounts/staff/{id}
     * Soft delete staff (set status = DEACTIVE).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteStaff(@PathVariable Long id) {
        accountStaffService.deleteStaff(id);
        return ResponseEntity.ok(Map.of(
                "message", "Staff deleted successfully"
        ));
    }
}
