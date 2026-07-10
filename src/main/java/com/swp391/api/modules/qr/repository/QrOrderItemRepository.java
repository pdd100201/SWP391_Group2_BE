package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QrOrderItemRepository extends JpaRepository<QrOrderItem, Long> {

    List<QrOrderItem> findByOrderId(Long orderId);

    List<QrOrderItem> findByOrderIdIn(Collection<Long> orderIds);
}
