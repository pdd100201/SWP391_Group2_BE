package com.swp391.api.modules.account.service.impl;

import com.swp391.api.modules.account.dto.CustomerResponse;
import com.swp391.api.modules.account.dto.CustomerUpdateRequest;
import com.swp391.api.modules.account.service.AccountCustomerService;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccountCustomerServiceImpl implements AccountCustomerService {

    private final CustomerRepository customerRepository;

    public AccountCustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomerList(String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();
        return customerRepository.findAll()
                .stream()
                .filter(c -> kw == null
                        || c.getFullName().toLowerCase().contains(kw)
                        || c.getCustomersEmail().toLowerCase().contains(kw))
                .map(this::toCustomerResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        return toCustomerResponse(findOrThrow(id));
    }

    @Override
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request) {
        Customer customer = findOrThrow(id);

        if (!customer.getCustomersEmail().equalsIgnoreCase(request.getEmail())
                && customerRepository.findByCustomersEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + request.getEmail());
        }

        customer.setFullName(request.getFullName());
        customer.setCustomersEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) {
            customer.setAvatarUrl(request.getAvatarUrl());
        }

        return toCustomerResponse(customerRepository.save(customer));
    }

    @Override
    public void deleteCustomer(Long id) {
        customerRepository.delete(findOrThrow(id));
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id: " + id));
    }

    private CustomerResponse toCustomerResponse(Customer customer) {
        CustomerResponse res = new CustomerResponse();
        res.setId(customer.getCustomerId());
        res.setFullName(customer.getFullName());
        res.setEmail(customer.getCustomersEmail());
        res.setPhone(customer.getPhone());
        res.setAvatarUrl(customer.getAvatarUrl());
        res.setCreatedAt(customer.getCreatedAt());
        res.setUpdatedAt(customer.getUpdatedAt());
        return res;
    }
}
