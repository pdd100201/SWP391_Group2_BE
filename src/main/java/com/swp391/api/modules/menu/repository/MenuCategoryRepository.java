package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    Optional<MenuCategory> findByNameIgnoreCase(String name);
    List<MenuCategory> findAllByOrderByNameAsc();
    List<MenuCategory> findByIsActiveTrueOrderByNameAsc();
}
