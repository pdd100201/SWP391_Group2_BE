package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.CustomerResponse;
import com.swp391.api.modules.user.dto.CustomerUpdateRequest;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;

import java.util.List;

public interface AccountCustomerService {

    /**
     * Lấy toàn bộ danh sách khách hàng, có thể lọc theo keyword/status.
     * Phân trang được xử lý ở phía frontend.
     */
    List<CustomerResponse> getCustomerList(String keyword, User.Status status);

    /**
     * Get detail of a single customer by userId (joins users + customers tables).
     */
    CustomerResponse getCustomerById(Long id);

    /**
     * Update customer information.
     */
    CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request);

    /**
     * Toggle ACTIVE/DEACTIVE status.
     */
    CustomerResponse updateCustomerStatus(Long id, StatusUpdateRequest request);

    /**
     * Delete a customer account (users + customers rows).
     */
    void deleteCustomer(Long id);
}
