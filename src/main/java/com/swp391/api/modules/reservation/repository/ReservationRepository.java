package com.swp391.api.modules.reservation.repository;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(String email);
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc();

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
}
