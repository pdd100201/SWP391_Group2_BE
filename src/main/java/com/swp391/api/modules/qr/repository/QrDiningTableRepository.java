package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrDiningTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrDiningTableRepository extends JpaRepository<QrDiningTable, Long> {
}
