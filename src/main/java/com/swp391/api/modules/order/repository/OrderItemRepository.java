package com.swp391.api.modules.order.repository;

import com.swp391.api.modules.order.entity.OrderItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Direct order-item lookup repository; most writes still go through RestaurantOrder.
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    Optional<OrderItem> findByIdAndOrderId(Long id, Long orderId);
    long countByOrder_Id(Long orderId);
}
