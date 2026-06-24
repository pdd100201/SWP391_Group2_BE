package com.swp391.api.modules.reservation.repository;

import com.swp391.api.modules.reservation.entity.Reservation;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
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
}
