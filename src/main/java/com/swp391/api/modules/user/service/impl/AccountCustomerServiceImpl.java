package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.CustomerResponse;
import com.swp391.api.modules.user.dto.CustomerUpdateRequest;
import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.AccountCustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccountCustomerServiceImpl implements AccountCustomerService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public AccountCustomerServiceImpl(UserRepository userRepository,
                                      CustomerRepository customerRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    // -------------------------------------------------------------------------
    // GET LIST
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getCustomerList(String keyword, User.Status status,
                                                          int page, int size) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findCustomers(kw, status, pageable);

        List<CustomerResponse> content = userPage.getContent()
                .stream()
                .map(user -> {
                    Customer customer = customerRepository.findByUser_UserId(user.getUserId())
                            .orElse(null);
                    return toCustomerResponse(user, customer);
                })
                .collect(Collectors.toList());

        return new PageResponse<>(content, userPage.getNumber(), userPage.getSize(),
                userPage.getTotalElements(), userPage.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        User user = findCustomerUserOrThrow(id);
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);
        return toCustomerResponse(user, customer);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Override
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request) {
        User user = findCustomerUserOrThrow(id);
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
            if (customer != null) {
                customer.setFullName(request.getFullName());
            }
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
            if (customer != null) {
                customer.setPhone(request.getPhone());
            }
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
            if (customer != null) {
                customer.setAvatarUrl(request.getAvatarUrl());
            }
        }
        if (request.getCustomersEmail() != null && !request.getCustomersEmail().isBlank()) {
            // Check uniqueness for customer email
            if (customer != null
                    && !customer.getCustomersEmail().equalsIgnoreCase(request.getCustomersEmail())
                    && customerRepository.existsByCustomersEmail(request.getCustomersEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Customer email already in use: " + request.getCustomersEmail());
            }
            if (customer != null) {
                customer.setCustomersEmail(request.getCustomersEmail());
            }
        }

        userRepository.save(user);
        if (customer != null) {
            customerRepository.save(customer);
        }
        return toCustomerResponse(user, customer);
    }

    // -------------------------------------------------------------------------
    // STATUS TOGGLE
    // -------------------------------------------------------------------------

    @Override
    public CustomerResponse updateCustomerStatus(Long id, StatusUpdateRequest request) {
        User user = findCustomerUserOrThrow(id);

        if (request.getStatus() == null) {
            user.setStatus(user.getStatus() == User.Status.ACTIVE
                    ? User.Status.DEACTIVE : User.Status.ACTIVE);
        } else {
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);
        return toCustomerResponse(saved, customer);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Override
    public void deleteCustomer(Long id) {
        User user = findCustomerUserOrThrow(id);
        // Delete customer profile first (FK constraint)
        customerRepository.findByUser_UserId(id).ifPresent(customerRepository::delete);
        userRepository.delete(user);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private User findCustomerUserOrThrow(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id: " + id));
        if (user.getRole() != User.Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Customer not found with id: " + id);
        }
        return user;
    }

    private CustomerResponse toCustomerResponse(User user, Customer customer) {
        CustomerResponse res = new CustomerResponse();
        res.setUserId(user.getUserId());
        res.setFullName(user.getFullName());
        res.setUserEmail(user.getUserEmail());
        res.setPhone(user.getPhone());
        res.setAvatarUrl(user.getAvatarUrl());
        res.setStatus(user.getStatus());
        res.setCreatedAt(user.getCreatedAt());
        res.setUpdatedAt(user.getUpdatedAt());

        if (customer != null) {
            res.setCustomerId(customer.getCustomerId());
            res.setCustomersEmail(customer.getCustomersEmail());
        }
        return res;
    }
}
