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
    List<Reservation> findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(String email);
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

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

    /**
     * 🚀 ĐÃ SỬA: Thay thế hàm cũ bằng câu JPQL Join qua bảng trung gian
     * logic: Tìm đơn Reservation join với danh sách tables 't', nơi mà t.id (hoặc t.tableId) bằng id truyền vào
     */
    @Query("""
            SELECT r 
            FROM Reservation r 
            JOIN r.tables t 
            WHERE t.id = :tableId 
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.ARRIVED
            """)
    Optional<Reservation> findActiveReservationByTableId(@Param("tableId") Long tableId);

    @Query("""
            SELECT r
            FROM Reservation r
            JOIN r.tables t
            WHERE t.id = :tableId
              AND r.status = com.swp391.api.modules.reservation.entity.ReservationStatus.CONFIRMED
            """)
    Optional<Reservation> findReservedReservationByTableId(@Param("tableId") Long tableId);
}
