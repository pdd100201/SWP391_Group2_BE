package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.CustomerResponse;
import com.swp391.api.modules.user.dto.CustomerUpdateRequest;
import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;

public interface AccountCustomerService {

    /**
     * Get paginated list of customer accounts with optional filters.
     */
    PageResponse<CustomerResponse> getCustomerList(String keyword, User.Status status,
                                                   int page, int size);

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
