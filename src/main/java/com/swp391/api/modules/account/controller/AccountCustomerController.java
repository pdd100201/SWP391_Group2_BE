package com.swp391.api.modules.account.controller;

import com.swp391.api.modules.account.dto.CustomerResponse;
import com.swp391.api.modules.account.dto.CustomerUpdateRequest;
import com.swp391.api.modules.account.service.AccountCustomerService;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCustomerList(
            @RequestParam(required = false) String keyword) {
        List<CustomerResponse> data = accountCustomerService.getCustomerList(keyword);
        return ResponseEntity.ok(Map.of("message", "Customer list retrieved successfully", "data", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCustomerById(@PathVariable Long id) {
        CustomerResponse data = accountCustomerService.getCustomerById(id);
        return ResponseEntity.ok(Map.of("message", "Customer retrieved successfully", "data", data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        CustomerResponse data = accountCustomerService.updateCustomer(id, request);
        return ResponseEntity.ok(Map.of("message", "Customer updated successfully", "data", data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCustomer(@PathVariable Long id) {
        accountCustomerService.deleteCustomer(id);
        return ResponseEntity.ok(Map.of("message", "Customer deleted successfully"));
    }
}
