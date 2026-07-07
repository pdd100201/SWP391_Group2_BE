package com.swp391.api;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class MenuPricingFlowTests {

    @Autowired
    private MenuService menuService;

    @Test
    void creatingMenuItemUsesDirectPrice() {
        MenuItemRequest request = new MenuItemRequest();
        request.setName("Direct Price Test Dish " + System.nanoTime());
        request.setCategory("Main Course");
        request.setDescription("A dish priced without recipe costing.");
        request.setImageUrl(null);
        request.setPrice(BigDecimal.valueOf(99000));

        MenuItemResponse response = menuService.create(request);

        assertEquals(BigDecimal.valueOf(99000), response.getPrice());
        assertEquals("AVAILABLE", response.getAvailability());
    }
}
