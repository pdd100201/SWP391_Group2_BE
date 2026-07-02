package com.swp391.api.modules.inventory.repository;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository để thao tác với bảng {@code inventory_items} trong cơ sở dữ liệu.
 *
 * <p>Kế thừa {@link JpaRepository} để có sẵn các phương thức CRUD cơ bản.
 * Spring Data JPA tự động sinh ra câu SQL dựa theo tên phương thức (derived query),
 * giúp không cần viết JPQL hay native SQL cho các truy vấn đơn giản.</p>
 */
public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);

    /**
     * Tìm mặt hàng theo tên chính xác, không phân biệt chữ hoa/thường.
     * Dùng để kiểm tra trùng tên trước khi tạo mặt hàng mới.
     *
     * @param itemName Tên cần tìm
     * @return Optional chứa mặt hàng nếu tìm thấy, rỗng nếu không có
     */
    Optional<InventoryItem> findByItemNameIgnoreCase(String itemName);

    /**
     * Tìm danh sách mặt hàng có tên chứa từ khóa, không phân biệt chữ hoa/thường.
     * Dùng cho chức năng tìm kiếm theo từ khóa trên UI.
     *
     * @param keyword Từ khóa tìm kiếm (tìm theo dạng LIKE %keyword%)
     * @return Danh sách mặt hàng khớp
     */
    List<InventoryItem> findByItemNameContainingIgnoreCase(String keyword);

    /**
     * Lọc mặt hàng theo danh mục chính xác, không phân biệt chữ hoa/thường.
     * Ví dụ: lọc tất cả mặt hàng thuộc danh mục "Meat".
     *
     * @param category Tên danh mục cần lọc
     * @return Danh sách mặt hàng trong danh mục đó
     */
    List<InventoryItem> findByCategoryIgnoreCase(String category);

    /**
     * Lọc mặt hàng theo trạng thái hoạt động (active/inactive).
     * Dùng để phân tách hàng đang dùng và hàng đã ngừng sử dụng.
     *
     * @param isActive true = đang hoạt động, false = đã vô hiệu hóa
     * @return Danh sách mặt hàng theo trạng thái
     */
    List<InventoryItem> findByIsActive(Boolean isActive);

    /**
     * Tìm kiếm kết hợp từ khóa trong tên VÀ lọc theo danh mục.
     * Dùng khi người dùng nhập đồng thời keyword và chọn category filter.
     *
     * @param keyword  Từ khóa tìm theo tên
     * @param category Danh mục cần lọc
     * @return Danh sách mặt hàng thỏa cả hai điều kiện
     */
    List<InventoryItem> findByItemNameContainingIgnoreCaseAndCategoryIgnoreCase(String keyword, String category);

    /**
     * Tìm kiếm kết hợp từ khóa trong tên VÀ lọc theo trạng thái hoạt động.
     * Dùng khi người dùng nhập keyword và chọn filter active/inactive.
     *
     * @param keyword  Từ khóa tìm theo tên
     * @param isActive Trạng thái hoạt động cần lọc
     * @return Danh sách mặt hàng thỏa cả hai điều kiện
     */
    List<InventoryItem> findByItemNameContainingIgnoreCaseAndIsActive(String keyword, Boolean isActive);

    /**
     * Tìm kiếm kết hợp cả ba điều kiện: từ khóa, danh mục và trạng thái hoạt động.
     * Đây là truy vấn đầy đủ nhất, dùng khi cả ba filter được áp dụng cùng lúc.
     *
     * @param keyword  Từ khóa tìm theo tên
     * @param category Danh mục cần lọc
     * @param isActive Trạng thái hoạt động cần lọc
     * @return Danh sách mặt hàng thỏa cả ba điều kiện
     */
    List<InventoryItem> findByItemNameContainingIgnoreCaseAndCategoryIgnoreCaseAndIsActive(
            String keyword, String category, Boolean isActive);
}
