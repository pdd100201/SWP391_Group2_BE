package com.swp391.api.modules.reservation.scheduler;

import com.swp391.api.modules.reservation.service.ReservationNoShowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationNoShowScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationNoShowScheduler.class);

    private final ReservationNoShowService reservationNoShowService;

    public ReservationNoShowScheduler(ReservationNoShowService reservationNoShowService) {
        this.reservationNoShowService = reservationNoShowService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void markNoShowsAndReleaseTables() {
        try {
            reservationNoShowService.markNoShowsAndReleaseTables();
        } catch (Exception exception) {
            log.error("Failed to mark no-show reservations and release tables", exception);
        }
    }
}
