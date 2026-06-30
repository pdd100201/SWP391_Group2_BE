package com.swp391.api.modules.reservation.scheduler;

import com.swp391.api.modules.reservation.service.ReservationNoShowService;
import com.swp391.api.modules.reservation.service.ReservationAutoTableLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationNoShowScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationNoShowScheduler.class);

    private final ReservationNoShowService reservationNoShowService;
    private final ReservationAutoTableLockService reservationAutoTableLockService;

    public ReservationNoShowScheduler(ReservationNoShowService reservationNoShowService,
                                      ReservationAutoTableLockService reservationAutoTableLockService) {
        this.reservationNoShowService = reservationNoShowService;
        this.reservationAutoTableLockService = reservationAutoTableLockService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void markNoShowsAndReleaseTables() {
        try {
            reservationNoShowService.markNoShowsAndReleaseTables();
            reservationAutoTableLockService.lockTablesForUpcomingReservations();
        } catch (Exception exception) {
            log.error("Failed to update scheduled reservation table states", exception);
        }
    }
}
