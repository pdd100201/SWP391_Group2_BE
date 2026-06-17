package com.swp391.api.modules.menu.repository;

import com.swp391.api.modules.menu.entity.MenuReservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MenuReservationRepository extends JpaRepository<MenuReservation, Long> {
    @Override
    @EntityGraph(attributePaths = {"ingredients", "ingredients.inventoryItem"})
    Optional<MenuReservation> findById(Long id);
}
