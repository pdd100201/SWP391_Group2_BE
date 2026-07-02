package com.swp391.api.modules.reservation.repository;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.table.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 1. Lấy lịch sử đặt bàn của một khách hàng dựa vào Email (Ưu tiên đơn mới nhất lên đầu).
    List<Reservation> findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(String email);

    // 2. Lấy toàn bộ đơn đặt bàn có trong hệ thống (Ưu tiên ngày giờ mới nhất lên đầu).
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc();

    // 3. Tìm đơn đặt bàn theo ID và KHÓA dòng dữ liệu lại (Lock), không cho các luồng khác sửa cùng lúc để tránh lỗi data.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    // 4. Lấy danh sách các đơn đặt bàn sắp đến (đã chốt lịch) trong một khoảng giờ để chuẩn bị đón khách.
    @Query("""
            SELECT r
            FROM Reservation r
            WHERE r.reservationDate = :reservationDate
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
              AND r.reservationTime >= :fromTime
              AND r.reservationTime <= :toTime
            ORDER BY r.reservationTime ASC, r.createdAt ASC
            """)
    List<Reservation> findUpcomingConfirmedReservations(@Param("reservationDate") LocalDate reservationDate,
                                                        @Param("fromTime") LocalTime fromTime,
                                                        @Param("toTime") LocalTime toTime);

    // 5. Lấy danh sách các đơn sắp đến trong khoảng giờ, kèm theo luôn thông tin họ ngồi bàn nào (để hiện lên màn hình sơ đồ bàn).
    @Query("""
            SELECT DISTINCT r
            FROM Reservation r
            LEFT JOIN FETCH r.tables t
            WHERE r.reservationDate = :reservationDate
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
              AND r.reservationTime >= :fromTime
              AND r.reservationTime <= :toTime
            ORDER BY r.reservationTime ASC, r.createdAt ASC
            """)
    List<Reservation> findConfirmedReservationsWithTablesInWindow(
            @Param("reservationDate") LocalDate reservationDate,
            @Param("fromTime") LocalTime fromTime,
            @Param("toTime") LocalTime toTime);

    // 6. Tìm các đơn khách đã chốt nhưng quá giờ hẹn mà chưa thấy đến (dùng để lọc ra danh sách khách "bùng lịch").
    @Query("""
            SELECT r
            FROM Reservation r
            WHERE r.reservationDate = :reservationDate
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
              AND r.reservationTime < :cutoffTime
            ORDER BY r.reservationTime ASC, r.createdAt ASC
            """)
    List<Reservation> findConfirmedNoShowCandidates(@Param("reservationDate") LocalDate reservationDate,
                                                    @Param("cutoffTime") LocalTime cutoffTime);

    // 7. Tìm các đơn khách "bùng lịch" kèm theo bàn họ đã gán để hệ thống tự động trả lại bàn trống cho khách khác.
    @Query("""
            SELECT DISTINCT r
            FROM Reservation r
            LEFT JOIN FETCH r.tables t
            WHERE r.reservationDate = :reservationDate
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
              AND r.reservationTime < :cutoffTime
            ORDER BY r.reservationTime ASC, r.createdAt ASC
            """)
    List<Reservation> findConfirmedNoShowCandidatesWithTables(
            @Param("reservationDate") LocalDate reservationDate,
            @Param("cutoffTime") LocalTime cutoffTime);

    // 8. Kiểm tra xem vào khung giờ khách muốn đặt, đã có bao nhiêu đơn khác đang giữ chỗ (để tránh bị trùng lịch/quá tải).
    @Query("""
            SELECT COUNT(r)
            FROM Reservation r
            WHERE r.reservationDate = :reservationDate
              AND r.status IN (com.swp391.api.modules.reservation.entity.ReservationStatus.PENDING,
                               com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED)
              AND r.reservationTime > :overlapStart
              AND r.reservationTime < :overlapEnd
            """)
    Long countActiveReservationsOverlappingWindow(@Param("reservationDate") LocalDate reservationDate,
                                                  @Param("overlapStart") LocalTime overlapStart,
                                                  @Param("overlapEnd") LocalTime overlapEnd);

    // 9. Tính tổng số lượng khách đã đặt chỗ trước trong khung giờ này (để xem nhà hàng còn đủ chỗ chứa hay không).
    @Query("""
            SELECT COALESCE(SUM(r.numberOfGuests), 0)
            FROM Reservation r
            WHERE r.reservationDate = :reservationDate
              AND r.status IN (com.swp391.api.modules.reservation.entity.ReservationStatus.PENDING,
                               com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED)
              AND r.reservationTime > :overlapStart
              AND r.reservationTime < :overlapEnd
            """)
    Long sumActiveBookedSeatsOverlappingWindow(@Param("reservationDate") LocalDate reservationDate,
                                               @Param("overlapStart") LocalTime overlapStart,
                                               @Param("overlapEnd") LocalTime overlapEnd);

    // 10. Tính xem trong khung giờ này đang có tổng cộng bao nhiêu khách thực tế đang ngồi ăn hoặc chắc chắn sẽ đến.
    @Query("""
            SELECT COALESCE(SUM(r.numberOfGuests), 0)
            FROM Reservation r
            WHERE r.reservationDate = :reservationDate
              AND r.status IN (com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED,
                               com.swp391.api.modules.reservation.entity.ReservationStatus.ARRIVED)
              AND r.reservationTime > :overlapStart
              AND r.reservationTime < :overlapEnd
            """)
    Long sumOccupiedSeatsForOverlappingWindow(@Param("reservationDate") LocalDate reservationDate,
                                              @Param("overlapStart") LocalTime overlapStart,
                                              @Param("overlapEnd") LocalTime overlapEnd);

    // 11. Tìm danh sách các bàn ĐÃ BỊ KHÓA (không cho đặt nữa) vì đã có khách khác ngồi hoặc đặt trước trong khung giờ này rồi.
    @Query("""
            SELECT DISTINCT t
            FROM Reservation r
            JOIN r.tables t
            WHERE r.reservationDate = :reservationDate
              AND r.status IN (com.swp391.api.modules.reservation.entity.ReservationStatus.PENDING,
                               com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED,
                               com.swp391.api.modules.reservation.entity.ReservationStatus.ARRIVED)
              AND r.reservationTime > :overlapStart
              AND r.reservationTime < :requestedEnd
            """)
    List<RestaurantTable> findUnavailableTablesForReservationWindow(
            @Param("reservationDate") LocalDate reservationDate,
            @Param("overlapStart") LocalTime overlapStart,
            @Param("requestedEnd") LocalTime requestedEnd);

    // 12. Tìm kiếm nhanh đơn đặt bàn để làm thủ tục Check-in cho khách (tìm theo ngày, trạng thái, hoặc gõ tên/số điện thoại).
    @Query("""
            SELECT r
            FROM Reservation r
            WHERE r.reservationDate = :date
              AND r.status IN :statuses
              AND (
                :search IS NULL
                OR :search = ''
                OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.phone) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY r.reservationTime ASC, r.createdAt ASC
            """)
    List<Reservation> findCheckInCandidates(@Param("date") LocalDate date,
                                            @Param("search") String search,
                                            @Param("statuses") List<ReservationStatus> statuses);

    // 13. Xem thử bàn này hiện tại có ông khách nào ĐANG NGỒI ĂN (ARRIVED) hay không thông qua bảng liên kết trung gian.
    @Query("""
            SELECT r 
            FROM Reservation r 
            JOIN r.tables t 
            WHERE t.id = :tableId 
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.ARRIVED
            """)
    Optional<Reservation> findActiveReservationByTableId(@Param("tableId") Long tableId);

    // 14. Xem thử bàn này đã có ai ĐẶT TRƯỚC (CONFIRMED) cho lát nữa hay chưa thông qua bảng liên kết trung gian.
    @Query("""
            SELECT r
            FROM Reservation r
            JOIN r.tables t
            WHERE t.id = :tableId
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
            """)
    Optional<Reservation> findReservedReservationByTableId(@Param("tableId") Long tableId);

    // 15. Tìm nhanh đơn đặt bàn dựa vào ID bàn và trạng thái (Hàm tự động tạo theo cơ chế Query Method của Spring Data JPA).
    Optional<Reservation> findByTableIdAndStatus(Long tableId, ReservationStatus status);
}