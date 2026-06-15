package com.swp391.api.modules.account.controller;

import com.swp391.api.modules.account.dto.StaffRequest;
import com.swp391.api.modules.account.dto.StaffResponse;
import com.swp391.api.modules.account.dto.StatusUpdateRequest;
import com.swp391.api.modules.account.service.AccountStaffService;

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

@RestController
@RequestMapping("/api/admin/accounts/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AccountStaffController {

    private final AccountStaffService accountStaffService;

    public AccountStaffController(AccountStaffService accountStaffService) {
        this.accountStaffService = accountStaffService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStaffList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive) {

        List<StaffResponse> data = accountStaffService.getStaffList(keyword, role, isActive);
        return ResponseEntity.ok(Map.of("message", "Staff list retrieved successfully", "data", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getStaffById(@PathVariable Long id) {
        StaffResponse data = accountStaffService.getStaffById(id);
        return ResponseEntity.ok(Map.of("message", "Staff retrieved successfully", "data", data));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createStaff(@Valid @RequestBody StaffRequest request) {
        StaffResponse data = accountStaffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Staff created successfully", "data", data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffRequest request) {
        StaffResponse data = accountStaffService.updateStaff(id, request);
        return ResponseEntity.ok(Map.of("message", "Staff updated successfully", "data", data));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStaffStatus(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest request) {
        StaffResponse data = accountStaffService.updateStaffStatus(id,
                request != null ? request : new StatusUpdateRequest());
        return ResponseEntity.ok(Map.of("message", "Staff status updated successfully", "data", data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteStaff(@PathVariable Long id) {
        accountStaffService.deleteStaff(id);
        return ResponseEntity.ok(Map.of("message", "Staff deleted successfully"));
    }
}
