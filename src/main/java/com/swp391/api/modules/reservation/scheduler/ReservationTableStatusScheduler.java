package com.swp391.api.modules.reservation.scheduler;

import com.swp391.api.modules.reservation.service.ReservationTableStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationTableStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationTableStatusScheduler.class);

    private final ReservationTableStatusService reservationTableStatusService;

    public ReservationTableStatusScheduler(ReservationTableStatusService reservationTableStatusService) {
        this.reservationTableStatusService = reservationTableStatusService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void reserveTablesBeforeReservationTime() {
        try {
            reservationTableStatusService.reserveTablesForUpcomingConfirmedReservations();
        } catch (Exception ex) {
            log.error("Failed to auto-reserve tables for upcoming reservations", ex);
        }
    }
}
