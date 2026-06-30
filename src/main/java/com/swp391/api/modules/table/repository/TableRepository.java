package com.swp391.api.modules.table.repository;

import com.swp391.api.modules.table.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM RestaurantTable t
            WHERE t.isActive = true
            """)
    Long sumActiveRestaurantSeats();

    @Query("""
            SELECT t
            FROM RestaurantTable t
            WHERE t.isActive = true
              AND t.status = com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE
            ORDER BY t.capacity ASC, t.id ASC
            """)
    List<RestaurantTable> findAvailableActiveTablesOrderByCapacityAsc();

    Optional<RestaurantTable> findByTableNumberIgnoreCase(String tableNumber);

    List<RestaurantTable> findByIsActive(Boolean isActive);

    List<RestaurantTable> findByTableType_TypeNameIgnoreCase(String tableType);

    List<RestaurantTable> findByStatus(RestaurantTable.TableStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTableNumberIgnoreCase(String tableNumber);
}
