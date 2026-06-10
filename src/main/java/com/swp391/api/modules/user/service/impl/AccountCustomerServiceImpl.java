package com.swp391.api.modules.user.service.impl;

import com.swp391.api.modules.user.dto.CustomerResponse;
import com.swp391.api.modules.user.dto.CustomerUpdateRequest;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.CustomerRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import com.swp391.api.modules.user.service.AccountCustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ quản lý tài khoản khách hàng (Customer).
 * Mỗi khách hàng gồm 2 bản ghi liên kết: User (tài khoản đăng nhập)
 * và Customer (hồ sơ khách hàng với email riêng).
 */
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
    // LẤY DANH SÁCH KHÁCH HÀNG (toàn bộ, phân trang xử lý ở frontend)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomerList(String keyword, User.Status status) {
        // Chuẩn hoá từ khoá tìm kiếm, nếu rỗng thì truyền null để truy vấn tất cả
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // Trả về toàn bộ danh sách matching, không giới hạn số lượng
        return userRepository.findCustomers(kw, status)
                .stream()
                .map(user -> {
                    Customer customer = customerRepository.findByUser_UserId(user.getUserId())
                            .orElse(null);
                    return toCustomerResponse(user, customer);
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // LẤY THÔNG TIN CHI TIẾT MỘT KHÁCH HÀNG THEO ID
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        User user = findCustomerUserOrThrow(id);
        // Tra thêm hồ sơ Customer để lấy customerId và email hồ sơ
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);
        return toCustomerResponse(user, customer);
    }

    // -------------------------------------------------------------------------
    // CẬP NHẬT THÔNG TIN KHÁCH HÀNG
    // -------------------------------------------------------------------------

    @Override
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request) {
        User user = findCustomerUserOrThrow(id);
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);

        // Cập nhật họ tên trên cả 2 bản ghi User và Customer
        user.setFullName(request.getFullName());
        if (customer != null) {
            customer.setFullName(request.getFullName());
        }

        // Cập nhật số điện thoại trên cả 2 bản ghi
        user.setPhone(request.getPhone());
        if (customer != null) {
            customer.setPhone(request.getPhone());
        }

        // Cập nhật avatar nếu có giá trị mới
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
            if (customer != null) {
                customer.setAvatarUrl(request.getAvatarUrl());
            }
        }

        // Kiểm tra email hồ sơ mới chưa bị dùng bởi khách hàng khác
        if (customer != null
                && !customer.getCustomersEmail().equalsIgnoreCase(request.getEmail())
                && customerRepository.existsByCustomersEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Customer email already in use: " + request.getEmail());
        }
        if (customer != null) {
            customer.setCustomersEmail(request.getEmail());
        }

        // Lưu thay đổi vào cơ sở dữ liệu
        userRepository.save(user);
        if (customer != null) {
            customerRepository.save(customer);
        }
        return toCustomerResponse(user, customer);
    }

    // -------------------------------------------------------------------------
    // CẬP NHẬT TRẠNG THÁI KHÁCH HÀNG (ACTIVE / DEACTIVE)
    // -------------------------------------------------------------------------

    @Override
    public CustomerResponse updateCustomerStatus(Long id, StatusUpdateRequest request) {
        User user = findCustomerUserOrThrow(id);

        if (request.getStatus() == null) {
            // Không truyền status → tự động đảo ngược trạng thái hiện tại
            user.setStatus(user.getStatus() == User.Status.ACTIVE
                    ? User.Status.DEACTIVE : User.Status.ACTIVE);
        } else {
            // Truyền status cụ thể → gán trực tiếp
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);
        Customer customer = customerRepository.findByUser_UserId(id).orElse(null);
        return toCustomerResponse(saved, customer);
    }

    // -------------------------------------------------------------------------
    // XOÁ TÀI KHOẢN KHÁCH HÀNG
    // -------------------------------------------------------------------------

    @Override
    public void deleteCustomer(Long id) {
        User user = findCustomerUserOrThrow(id);
        // Xoá hồ sơ Customer trước để tránh lỗi ràng buộc khoá ngoại (FK constraint)
        customerRepository.findByUser_UserId(id).ifPresent(customerRepository::delete);
        userRepository.delete(user);
    }

    // -------------------------------------------------------------------------
    // CÁC PHƯƠNG THỨC HỖ TRỢ (PRIVATE HELPERS)
    // -------------------------------------------------------------------------

    /**
     * Tìm user theo id và đảm bảo đó là tài khoản khách hàng (role = CUSTOMER).
     * Ném 404 nếu không tìm thấy hoặc không phải CUSTOMER.
     */
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

    /**
     * Chuyển đổi cặp (User, Customer) sang CustomerResponse DTO để trả về cho client.
     * Email hiển thị lấy từ hồ sơ Customer (customersEmail), không phải email đăng nhập.
     */
    private CustomerResponse toCustomerResponse(User user, Customer customer) {
        CustomerResponse res = new CustomerResponse();
        res.setId(user.getUserId());
        res.setFullName(user.getFullName());
        res.setPhone(user.getPhone());
        res.setAvatarUrl(user.getAvatarUrl());
        res.setStatus(user.getStatus());
        res.setCreatedAt(user.getCreatedAt());
        res.setUpdatedAt(user.getUpdatedAt());

        // Gán thêm thông tin từ hồ sơ Customer nếu tồn tại
        if (customer != null) {
            res.setCustomerId(customer.getCustomerId());
            res.setEmail(customer.getCustomersEmail());
        }
        return res;
    }
}
