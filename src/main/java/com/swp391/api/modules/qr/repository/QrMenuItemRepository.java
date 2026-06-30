package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QrMenuItemRepository extends JpaRepository<QrMenuItem, Long> {

    List<QrMenuItem> findByIsActive(Boolean isActive);
}
