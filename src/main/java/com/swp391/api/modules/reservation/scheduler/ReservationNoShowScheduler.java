package com.swp391.api.modules.reservation.scheduler;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationNoShowScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationNoShowScheduler.class);
    private static final int NO_SHOW_GRACE_MINUTES = 15;

    private final ReservationRepository reservationRepository;

    public ReservationNoShowScheduler(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void cancelConfirmedNoShows() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime cutoffTime = now.minusMinutes(NO_SHOW_GRACE_MINUTES);
        if (cutoffTime.isAfter(now)) {
            return;
        }

        List<Reservation> noShowReservations = reservationRepository.findConfirmedNoShowCandidates(today, cutoffTime);
        if (noShowReservations.isEmpty()) {
            return;
        }

        noShowReservations.forEach(reservation -> reservation.setStatus(ReservationStatus.CANCELLED));
        reservationRepository.saveAll(noShowReservations);

        List<Long> canceledReservationIds = noShowReservations.stream()
                .map(Reservation::getReservationId)
                .toList();
        log.info("Auto-cancelled no-show reservation IDs: {}", canceledReservationIds);
    }
}
