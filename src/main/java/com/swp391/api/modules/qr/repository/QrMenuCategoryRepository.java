package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrMenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrMenuCategoryRepository extends JpaRepository<QrMenuCategory, Long> {
}
