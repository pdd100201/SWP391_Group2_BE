package com.swp391.api.modules.account.service;

import com.swp391.api.modules.account.dto.CustomerResponse;
import com.swp391.api.modules.account.dto.CustomerUpdateRequest;
import java.util.List;

public interface AccountCustomerService {

    List<CustomerResponse> getCustomerList(String keyword);

    CustomerResponse getCustomerById(Long id);

    CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request);

    void deleteCustomer(Long id);
}
