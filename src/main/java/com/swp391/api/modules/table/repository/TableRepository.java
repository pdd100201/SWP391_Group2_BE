package com.swp391.api.modules.table.repository;

import com.swp391.api.modules.table.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository để thao tác với bảng {@code restaurant_tables} trong cơ sở dữ liệu.
 *
 * <p>Kế thừa {@link JpaRepository} để có sẵn các phương thức CRUD cơ bản.
 * Spring Data JPA tự động sinh ra câu SQL dựa theo tên phương thức (derived query).</p>
 */
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    @Query("""
            SELECT COUNT(t)
            FROM RestaurantTable t
            WHERE t.capacity >= :requestedGuests
              AND t.status = com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE
              AND t.isActive = true
            """)
    Long countAvailableTablesThatFitGuests(@Param("requestedGuests") Integer requestedGuests);

    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM RestaurantTable t
            """)
    Long sumTotalRestaurantSeats();

    /**
     * Tìm bàn theo số bàn chính xác, không phân biệt chữ hoa/thường.
     * Dùng để kiểm tra trùng số bàn trước khi tạo bàn mới.
     *
     * @param tableNumber Số bàn cần tìm
     * @return Optional chứa bàn nếu tìm thấy, hoặc empty nếu không
     */
    Optional<RestaurantTable> findByTableNumberIgnoreCase(String tableNumber);

    /**
     * Lấy tất cả bàn đang hoạt động (active = true).
     *
     * @return Danh sách bàn đang hoạt động
     */
    List<RestaurantTable> findByIsActive(Boolean isActive);

    /**
     * Lấy các bàn theo loại bàn.
     * 
     * @param tableType Loại bàn cần tìm
     * @return Danh sách bàn có loại này
     */
    List<RestaurantTable> findByTableType_TypeNameIgnoreCase(String tableType);

    /**
     * Lấy các bàn theo trạng thái.
     * 
     * @param status Trạng thái cần tìm
     * @return Danh sách bàn có trạng thái này
     */
    List<RestaurantTable> findByStatus(RestaurantTable.TableStatus status);

    /**
     * Kiểm tra xem số bàn đã tồn tại chưa.
     *
     * @param tableNumber Số bàn cần kiểm tra
     * @return true nếu tồn tại, false nếu không
     */
    boolean existsByTableNumberIgnoreCase(String tableNumber);
}
