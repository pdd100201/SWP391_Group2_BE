package com.swp391.api.modules.account.service;

import com.swp391.api.modules.account.dto.CustomerResponse;
import com.swp391.api.modules.account.dto.CustomerUpdateRequest;
import com.swp391.api.modules.account.dto.StatusUpdateRequest;

import java.util.List;

public interface AccountCustomerService {

    List<CustomerResponse> getCustomerList(String keyword, Boolean isActive);

    CustomerResponse getCustomerById(Long id);

    CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request);

    CustomerResponse updateCustomerStatus(Long id, StatusUpdateRequest request);

    void deleteCustomer(Long id);
}
