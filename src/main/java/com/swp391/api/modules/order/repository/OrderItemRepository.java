package com.swp391.api.modules.order.repository;

import com.swp391.api.modules.order.entity.OrderItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Truy vấn trực tiếp dòng món; phần lớn thao tác ghi vẫn đi qua aggregate RestaurantOrder.
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Điều kiện cả itemId và orderId tránh lấy nhầm món thuộc order khác.
    Optional<OrderItem> findByIdAndOrderId(Long id, Long orderId);

    // Đếm số dòng món vật lý trong một order, không phải tổng quantity.
    long countByOrder_Id(Long orderId);
}
