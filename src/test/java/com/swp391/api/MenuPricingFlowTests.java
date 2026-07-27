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

/**
 * Test tích hợp tầng service và database để xác nhận luồng tạo món lưu đúng giá bán trực tiếp.
 * @Transactional giúp dữ liệu test được rollback sau khi test kết thúc.
 */
@SpringBootTest
@Transactional
class MenuPricingFlowTests {

    // Spring truyền implementation thật của MenuService để test đúng luồng ứng dụng.
    @Autowired
    private MenuService menuService;

    @Test
    void creatingMenuItemUsesDirectPrice() {
        // Tên kèm nanoTime tránh trùng với món đã có trong dữ liệu khởi tạo.
        MenuItemRequest request = new MenuItemRequest();
        request.setName("Direct Price Test Dish " + System.nanoTime());
        request.setCategory("Main Course");
        request.setDescription("A dish priced without recipe costing.");
        request.setImageUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg");
        request.setPrice(BigDecimal.valueOf(99000));

        MenuItemResponse response = menuService.create(request);

        // Món mới phải giữ nguyên giá frontend gửi và mặc định ở trạng thái đang phục vụ.
        assertEquals(BigDecimal.valueOf(99000), response.getPrice());
        assertEquals("AVAILABLE", response.getAvailability());
    }
}
