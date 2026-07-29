package com.swp391.api.modules.menu.service;

import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;

import java.util.List;

/**
 * Hợp đồng nghiệp vụ của module Menu.
 * Controller chỉ gọi các hàm tại đây và không cần biết lớp triển khai truy cập database như thế nào.
 */
public interface MenuService {
    // Lấy toàn bộ món.
    List<MenuItemResponse> getAll();

    // Lấy một món theo ID.
    MenuItemResponse getById(Long id);

    // Tạo món mới từ dữ liệu frontend gửi lên.
    MenuItemResponse create(MenuItemRequest request);

    // Cập nhật món đã tồn tại.
    MenuItemResponse update(Long id, MenuItemRequest request);

    // Đảo trạng thái giữa đang phục vụ và ngừng phục vụ.
    MenuItemResponse toggleActive(Long id);
}
