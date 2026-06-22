package com.swp391.api.modules.reservation.repository;

import com.swp391.api.modules.reservation.entity.Reservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(String email);
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc();
}
