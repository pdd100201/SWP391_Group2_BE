package com.swp391.api.modules.order.repository;

import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Order persistence queries, including pessimistic locks for inventory-sensitive updates.
public interface OrderRepository extends JpaRepository<RestaurantOrder, Long> {
    Optional<RestaurantOrder> findByReservationReservationId(Long reservationId);
    Optional<RestaurantOrder> findByPublicAccessToken(String publicAccessToken);
    List<RestaurantOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<RestaurantOrder> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RestaurantOrder o where o.id = :id")
    Optional<RestaurantOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RestaurantOrder o where o.publicAccessToken = :token")
    Optional<RestaurantOrder> findByTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from RestaurantOrder o
            where o.status = com.swp391.api.modules.order.entity.OrderStatus.OPEN
              and o.reservation.tableId = :tableId
            order by o.createdAt desc
            """)
    List<RestaurantOrder> findOpenOrdersByTableIdForUpdate(@Param("tableId") Long tableId);
}
