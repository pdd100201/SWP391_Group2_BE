package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QrOrderRepository extends JpaRepository<QrOrder, Long> {

    Optional<QrOrder> findFirstByTableIdAndStatus(Long tableId, String status);

    Optional<QrOrder> findFirstByRestaurantOrderIdOrderByCreatedAtDesc(Long restaurantOrderId);

    List<QrOrder> findAllByOrderByCreatedAtDesc();

    long countByOrderCodeStartingWith(String prefix);
}
