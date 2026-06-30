package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationNoShowService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationNoShowServiceImpl implements ReservationNoShowService {

    private static final int NO_SHOW_GRACE_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    public ReservationNoShowServiceImpl(ReservationRepository reservationRepository,
                                        TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    @Override
    @Transactional
    public void markNoShowsAndReleaseTables() {
        LocalTime cutoffTime = LocalTime.now().minusMinutes(NO_SHOW_GRACE_MINUTES);
        List<Reservation> reservations = reservationRepository.findConfirmedNoShowCandidatesWithTables(
                LocalDate.now(),
                cutoffTime
        );

        for (Reservation reservation : reservations) {
            reservation.setStatus(ReservationStatus.NO_SHOW);
            releaseReservedTables(reservation);
        }

        reservationRepository.saveAll(reservations);
    }

    private void releaseReservedTables(Reservation reservation) {
        List<RestaurantTable> tables = reservation.getTables();
        if (tables == null || tables.isEmpty()) {
            return;
        }

        tables.stream()
                .filter(table -> table.getStatus() == RestaurantTable.TableStatus.RESERVED)
                .forEach(table -> table.setStatus(RestaurantTable.TableStatus.AVAILABLE));
        tableRepository.saveAll(tables);
    }
}
