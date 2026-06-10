package com.swp391.api.modules.user.controller;

import com.swp391.api.modules.user.dto.StaffRequest;
import com.swp391.api.modules.user.dto.StaffResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.service.AccountStaffService;

import java.util.List;
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

/**
 * API quản lý tài khoản nhân viên (Staff).
 * Chỉ ADMIN và MANAGER mới có quyền truy cập.
 * Phân quyền chi tiết (MANAGER không được thao tác trên ADMIN/MANAGER)
 * được xử lý trong AccountStaffServiceImpl.
 */
@RestController
@RequestMapping("/api/admin/accounts/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AccountStaffController {

    private final AccountStaffService accountStaffService;

    public AccountStaffController(AccountStaffService accountStaffService) {
        this.accountStaffService = accountStaffService;
    }

    // -------------------------------------------------------------------------
    // LẤY DANH SÁCH NHÂN VIÊN
    // GET /api/admin/accounts/staff?keyword=&role=&status=&page=&size=
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStaffList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.Status status) {

        List<StaffResponse> data = accountStaffService.getStaffList(keyword, role, status);
        return ResponseEntity.ok(Map.of(
                "message", "Staff list retrieved successfully",
                "data", data
        ));
    }

    // -------------------------------------------------------------------------
    // LẤY CHI TIẾT MỘT NHÂN VIÊN
    // GET /api/admin/accounts/staff/{id}
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getStaffById(@PathVariable Long id) {
        StaffResponse data = accountStaffService.getStaffById(id);
        return ResponseEntity.ok(Map.of(
                "message", "Staff retrieved successfully",
                "data", data
        ));
    }

    // -------------------------------------------------------------------------
    // TẠO TÀI KHOẢN NHÂN VIÊN MỚI
    // POST /api/admin/accounts/staff
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<Map<String, Object>> createStaff(@Valid @RequestBody StaffRequest request) {
        StaffResponse data = accountStaffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Staff created successfully",
                "data", data
        ));
    }

    // -------------------------------------------------------------------------
    // CẬP NHẬT THÔNG TIN NHÂN VIÊN
    // PUT /api/admin/accounts/staff/{id}
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // ĐỔI TRẠNG THÁI NHÂN VIÊN (ACTIVE / DEACTIVE)
    // PATCH /api/admin/accounts/staff/{id}/status
    // Body tuỳ chọn: {"status": "ACTIVE"} hoặc để trống để tự đảo ngược
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // XOÁ TÀI KHOẢN NHÂN VIÊN
    // DELETE /api/admin/accounts/staff/{id}
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteStaff(@PathVariable Long id) {
        accountStaffService.deleteStaff(id);
        return ResponseEntity.ok(Map.of(
                "message", "Staff deleted successfully"
        ));
    }
}
