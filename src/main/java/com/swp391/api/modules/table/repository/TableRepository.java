package com.swp391.api.modules.table.repository;

import com.swp391.api.modules.table.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository dung de truy van bang restaurant_tables.
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    // Dem so ban active, dang trong, va du suc chua cho so khach yeu cau.
    @Query("""
            SELECT COUNT(t)
            FROM RestaurantTable t
            WHERE t.capacity >= :requestedGuests
              AND t.status = com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE
              AND t.isActive = true
            """)
    Long countAvailableTablesThatFitGuests(@Param("requestedGuests") Integer requestedGuests);

    // Tong so ghe cua tat ca ban trong database.
    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM RestaurantTable t
            """)
    Long sumTotalRestaurantSeats();

    // Tong so ghe cua cac ban dang active.
    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM RestaurantTable t
            WHERE t.isActive = true
            """)
    Long sumActiveRestaurantSeats();

    // Tong so ghe cua cac ban active va dang trong.
    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM RestaurantTable t
            WHERE t.isActive = true
              AND t.status = com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE
            """)
    Long sumAvailableActiveRestaurantSeats();

    // Lay cac ban active, dang trong; sap xep ban nho truoc.
    @Query("""
            SELECT t
            FROM RestaurantTable t
            WHERE t.isActive = true
              AND t.status = com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE
            ORDER BY t.capacity ASC, t.id ASC
            """)
    List<RestaurantTable> findAvailableActiveTablesOrderByCapacityAsc();

    // Tim ban theo so ban, khong phan biet chu hoa/thuong.
    Optional<RestaurantTable> findByTableNumberIgnoreCase(String tableNumber);

    // Lay danh sach ban theo active/inactive.
    List<RestaurantTable> findByIsActive(Boolean isActive);

    // Lay danh sach ban theo ten loai ban, vi du "VIP Room".
    List<RestaurantTable> findByTableType_TypeNameIgnoreCase(String tableType);

    // Lay danh sach ban theo trang thai: AVAILABLE, OCCUPIED, RESERVED, CLEANING.
    List<RestaurantTable> findByStatus(RestaurantTable.TableStatus status);

    // Tim ban theo id va khoa dong do khi update de tranh sua cung luc.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);

    // Kiem tra so ban da ton tai chua, dung de chan tao trung.
    boolean existsByTableNumberIgnoreCase(String tableNumber);
}
