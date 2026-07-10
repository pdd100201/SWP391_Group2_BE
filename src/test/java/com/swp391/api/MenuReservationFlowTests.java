package com.swp391.api;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.dto.ReservationRequest;
import com.swp391.api.modules.menu.dto.ReservationResponse;
import com.swp391.api.modules.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class MenuReservationFlowTests {

    @Autowired
    private MenuService menuService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void servingReservationDeductsPhysicalInventoryOnlyAtServedState() {
        MenuItemResponse salad = menuService.getAll().stream()
                .filter(item -> item.getName().equals("Fresh Garden Salad"))
                .findFirst()
                .orElseThrow();
        InventoryItem before = inventoryRepository.findByItemNameIgnoreCase("Lettuce").orElseThrow();
        Long lettuceId = before.getId();
        double requiredLettuceQuantity = 0.15;
        double physicalBefore = before.getQuantity();
        double reservedBefore = before.getReservedQuantity();

        ReservationRequest request = new ReservationRequest();
        request.setServings(1);
        request.setReferenceCode("TEST-SERVED-FLOW");
        ReservationResponse reservation = menuService.reserve(salad.getId(), request);

        InventoryItem afterReserve = inventoryRepository.findById(lettuceId).orElseThrow();
        assertEquals(physicalBefore, afterReserve.getQuantity(), 0.000001);
        assertEquals(reservedBefore + requiredLettuceQuantity, afterReserve.getReservedQuantity(), 0.000001);

        ReservationResponse served = menuService.serve(reservation.getId());
        InventoryItem afterServe = inventoryRepository.findById(lettuceId).orElseThrow();

        assertEquals("SERVED", served.getStatus());
        assertEquals(physicalBefore - requiredLettuceQuantity, afterServe.getQuantity(), 0.000001);
        assertEquals(reservedBefore, afterServe.getReservedQuantity(), 0.000001);
    }
}
