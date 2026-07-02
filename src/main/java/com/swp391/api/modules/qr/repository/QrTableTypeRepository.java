package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrTableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrTableTypeRepository extends JpaRepository<QrTableType, Long> {

    boolean existsByTypeName(String typeName);
}
