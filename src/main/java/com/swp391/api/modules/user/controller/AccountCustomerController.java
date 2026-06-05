package com.swp391.api.modules.user.controller;

import com.swp391.api.modules.user.dto.CustomerResponse;
import com.swp391.api.modules.user.dto.CustomerUpdateRequest;
import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.service.AccountCustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/accounts/customer")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AccountCustomerController {

    private final AccountCustomerService accountCustomerService;

    public AccountCustomerController(AccountCustomerService accountCustomerService) {
        this.accountCustomerService = accountCustomerService;
    }

    /**
     * GET /api/admin/accounts/customer
     * Lấy danh sách customer có phân trang, tìm kiếm, lọc.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCustomerList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) User.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<CustomerResponse> data = accountCustomerService.getCustomerList(keyword, status, page, size);
        return ResponseEntity.ok(Map.of(
                "message", "Customer list retrieved successfully",
                "data", data
        ));
    }

    /**
     * GET /api/admin/accounts/customer/{id}
     * Xem chi tiết customer (join bảng customers).
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCustomerById(@PathVariable Long id) {
        CustomerResponse data = accountCustomerService.getCustomerById(id);
        return ResponseEntity.ok(Map.of(
                "message", "Customer retrieved successfully",
                "data", data
        ));
    }

    /**
     * PUT /api/admin/accounts/customer/{id}
     * Sửa thông tin customer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        CustomerResponse data = accountCustomerService.updateCustomer(id, request);
        return ResponseEntity.ok(Map.of(
                "message", "Customer updated successfully",
                "data", data
        ));
    }

    /**
     * PATCH /api/admin/accounts/customer/{id}/status
     * Bật/tắt trạng thái ACTIVE/DEACTIVE.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateCustomerStatus(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest request) {
        CustomerResponse data = accountCustomerService.updateCustomerStatus(id,
                request != null ? request : new StatusUpdateRequest());
        return ResponseEntity.ok(Map.of(
                "message", "Customer status updated successfully",
                "data", data
        ));
    }

    /**
     * DELETE /api/admin/accounts/customer/{id}
     * Xóa customer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCustomer(@PathVariable Long id) {
        accountCustomerService.deleteCustomer(id);
        return ResponseEntity.ok(Map.of(
                "message", "Customer deleted successfully"
        ));
    }
}
