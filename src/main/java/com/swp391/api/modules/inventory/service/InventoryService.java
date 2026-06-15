package com.swp391.api.modules.inventory.service;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateInventoryItemRequest;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;

import java.util.List;

/**
 * Interface định nghĩa các nghiệp vụ quản lý kho nguyên liệu (Inventory).
 *
 * <p>Tách interface và implementation theo nguyên tắc DIP (Dependency Inversion Principle)
 * giúp dễ dàng mock trong unit test và thay thế implementation nếu cần.</p>
 *
 * <p>Implementation chính: {@link com.swp391.api.modules.inventory.service.impl.InventoryServiceImpl}</p>
 */
public interface InventoryService {

    /**
     * Lấy toàn bộ danh sách mặt hàng trong kho, bao gồm cả active lẫn inactive.
     *
     * @return Danh sách tất cả mặt hàng dưới dạng DTO response
     */
    List<InventoryItemResponse> getAllItems();

    /**
     * Lấy thông tin chi tiết một mặt hàng theo ID.
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy.
     *
     * @param id ID của mặt hàng cần lấy
     * @return Thông tin mặt hàng dưới dạng DTO response
     */
    InventoryItemResponse getItemById(Long id);

    /**
     * Tạo mặt hàng mới trong kho.
     * Ném {@code ResponseStatusException(409)} nếu tên mặt hàng đã tồn tại.
     *
     * @param request Dữ liệu mặt hàng mới từ client
     * @return Mặt hàng vừa tạo dưới dạng DTO response
     */
    InventoryItemResponse createItem(InventoryItemRequest request);

    /**
     * Chỉnh sửa danh mục, đơn vị, số lượng, ngưỡng tối thiểu, giá và nhà cung cấp.
     *
     * @param id      ID mặt hàng cần chỉnh sửa
     * @param request Các thông tin vận hành mới
     * @return Mặt hàng sau khi cập nhật
     */
    InventoryItemResponse updateItem(Long id, UpdateInventoryItemRequest request);

    /**
     * Cập nhật số lượng tồn kho của một mặt hàng.
     * Trạng thái sẽ được tính lại tự động sau khi cập nhật (trừ khi có override).
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy mặt hàng.
     *
     * @param id      ID mặt hàng cần cập nhật
     * @param request Số lượng mới và ghi chú
     * @return Mặt hàng sau khi cập nhật
     */
    InventoryItemResponse updateQuantity(Long id, UpdateQuantityRequest request);

    /**
     * Ghi đè thủ công hoặc reset trạng thái tồn kho của mặt hàng.
     * Ném {@code ResponseStatusException(400)} nếu giá trị status không hợp lệ.
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy mặt hàng.
     *
     * @param id      ID mặt hàng cần cập nhật trạng thái
     * @param request Trạng thái ghi đè (hoặc null để reset)
     * @return Mặt hàng sau khi cập nhật trạng thái
     */
    InventoryItemResponse updateStatus(Long id, UpdateStatusRequest request);

    /**
     * Bật/tắt trạng thái hoạt động của mặt hàng (soft delete/restore).
     * Nếu đang active thì chuyển sang inactive và ngược lại.
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy mặt hàng.
     *
     * @param id ID mặt hàng cần đổi trạng thái
     * @return Mặt hàng sau khi toggle
     */
    InventoryItemResponse toggleActive(Long id);

    /**
     * Tìm kiếm mặt hàng với nhiều điều kiện lọc kết hợp.
     * Các tham số đều tùy chọn - nếu null thì không áp dụng điều kiện đó.
     *
     * @param keyword  Từ khóa tìm theo tên (LIKE search, không phân biệt hoa thường)
     * @param category Lọc theo danh mục chính xác (không phân biệt hoa thường)
     * @param isActive Lọc theo trạng thái hoạt động (null = lấy tất cả)
     * @return Danh sách mặt hàng thỏa điều kiện tìm kiếm
     */
    List<InventoryItemResponse> searchItems(String keyword, String category, Boolean isActive);
}
