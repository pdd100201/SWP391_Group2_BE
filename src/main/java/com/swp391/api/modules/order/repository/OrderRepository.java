package com.swp391.api.modules.order.repository;

import com.swp391.api.modules.order.entity.RestaurantOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Lớp truy cập dữ liệu Order, gồm các truy vấn khóa pessimistic cho thao tác đồng thời.
public interface OrderRepository extends JpaRepository<RestaurantOrder, Long> {
    // Đọc toàn bộ order của một reservation, mới nhất trước.
    List<RestaurantOrder> findAllByReservationReservationIdOrderByCreatedAtDesc(Long reservationId);

    // Kiểm tra một bàn trong reservation đã có order hay chưa để chống tạo trùng.
    Optional<RestaurantOrder> findByReservationReservationIdAndTableId(Long reservationId, Long tableId);

    // Token công khai dùng cho thao tác chỉ đọc; request ghi dùng hàm khóa bên dưới.
    Optional<RestaurantOrder> findByPublicAccessToken(String publicAccessToken);
    List<RestaurantOrder> findAllByOrderByCreatedAtDesc();

    // Khóa bản ghi order đến hết transaction để hai request không cập nhật chồng nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RestaurantOrder o where o.id = :id")
    Optional<RestaurantOrder> findByIdForUpdate(@Param("id") Long id);

    // Cùng cơ chế khóa nhưng tra theo token QR của khách.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RestaurantOrder o where o.publicAccessToken = :token")
    Optional<RestaurantOrder> findByTokenForUpdate(@Param("token") String token);

    // Khóa các order OPEN của một bàn; QR flow dùng để tái sử dụng order đang hoạt động.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from RestaurantOrder o
            where o.status = com.swp391.api.modules.order.entity.OrderStatus.OPEN
              and o.tableId = :tableId
            order by o.createdAt desc
            """)
    List<RestaurantOrder> findOpenOrdersByTableIdForUpdate(@Param("tableId") Long tableId);
}
