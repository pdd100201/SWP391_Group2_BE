package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrOrderRepository extends JpaRepository<QrOrder, Long> {
}
