package com.swp391.api.modules.table.service;

import com.swp391.api.modules.table.dto.TableRequest;
import com.swp391.api.modules.table.dto.TableResponse;
import com.swp391.api.modules.table.dto.UpdateTableStatusRequest;
import com.swp391.api.modules.table.entity.RestaurantTable;

import java.util.List;

/**
 * Interface định nghĩa các nghiệp vụ quản lý bàn nhà hàng (Table).
 *
 * <p>Tách interface và implementation theo nguyên tắc DIP (Dependency Inversion Principle)
 * giúp dễ dàng mock trong unit test và thay thế implementation nếu cần.</p>
 *
 * <p>Implementation chính: {@link com.swp391.api.modules.table.service.impl.TableServiceImpl}</p>
 */
public interface TableService {

    /**
     * Lấy toàn bộ danh sách bàn, bao gồm cả active lẫn inactive.
     *
     * @return Danh sách tất cả bàn dưới dạng DTO response
     */
    List<TableResponse> getAllTables();

    /**
     * Lấy danh sách bàn đang hoạt động (isActive = true).
     *
     * @return Danh sách bàn đang hoạt động
     */
    List<TableResponse> getActiveTables();

    /**
     * Lấy thông tin chi tiết một bàn theo ID.
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy.
     *
     * @param id ID của bàn cần lấy
     * @return Thông tin bàn dưới dạng DTO response
     */
    TableResponse getTableById(Long id);

    /**
     * Lấy danh sách bàn theo loại (tableType).
     *
     * @param tableType Loại bàn (Main Hall, VIP Room, Patio...)
     * @return Danh sách bàn có loại này
     */
    List<TableResponse> getTablesByType(String tableType);

    /**
     * Lấy danh sách bàn theo trạng thái.
     *
     * @param status Trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED, CLEANING)
     * @return Danh sách bàn có trạng thái này
     */
    List<TableResponse> getTablesByStatus(String status);

    /**
     * Tạo bàn mới.
     * Ném {@code ResponseStatusException(409)} nếu số bàn đã tồn tại.
     *
     * @param request Dữ liệu bàn mới từ client
     * @return Bàn vừa tạo dưới dạng DTO response
     */
    TableResponse createTable(TableRequest request);

    /**
     * Cập nhật thông tin bàn (tên, loại, sức chứa, QR code).
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy bàn.
     * Ném {@code ResponseStatusException(409)} nếu số bàn mới đã tồn tại (khi cập nhật số bàn).
     *
     * @param id      ID bàn cần cập nhật
     * @param request Thông tin bàn mới
     * @return Bàn sau khi cập nhật
     */
    TableResponse updateTable(Long id, TableRequest request);

    /**
     * Cập nhật trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED, CLEANING).
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy bàn.
     * Ném {@code ResponseStatusException(400)} nếu trạng thái không hợp lệ.
     *
     * @param id      ID bàn cần cập nhật
     * @param request Trạng thái mới
     * @return Bàn sau khi cập nhật trạng thái
     */
    TableResponse updateTableStatus(Long id, UpdateTableStatusRequest request);

    /**
     * Bật/tắt trạng thái hoạt động của bàn (soft delete/restore).
     * Nếu đang active thì chuyển sang inactive và ngược lại.
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy bàn.
     *
     * @param id ID bàn cần toggle
     * @return Bàn sau khi toggle trạng thái
     */
    TableResponse toggleTableActive(Long id);

    /**
     * Xóa bàn khỏi hệ thống (hard delete).
     * Ném {@code ResponseStatusException(404)} nếu không tìm thấy bàn.
     *
     * @param id ID bàn cần xóa
     */
    void deleteTable(Long id);
}
