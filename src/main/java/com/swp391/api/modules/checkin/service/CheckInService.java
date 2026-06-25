package com.swp391.api.modules.checkin.service;

import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for the restaurant check-in flow.
 */
public interface CheckInService {

    /**
     * Finds reservations that can still be checked in for the selected date.
     *
     * @param date reservation date to search
     * @param search optional guest name or phone keyword
     * @return pending and confirmed reservations matching the filters
     */
    List<ReservationResponse> getCheckInReservations(LocalDate date, String search);

    /**
     * Assigns an available table to a valid reservation in one transaction.
     *
     * @param request check-in assignment payload
     * @return updated reservation data
     */
    ReservationResponse processCheckInTransaction(CheckInRequest request);
}
