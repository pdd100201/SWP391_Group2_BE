package com.swp391.api.modules.reservation.service;

import com.swp391.api.modules.reservation.dto.AssignTablesRequest;
import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import java.util.List;

public interface ReservationService {
    ReservationResponse createReservation(CreateReservationRequest request);
    List<ReservationResponse> getMyReservations();
    List<ReservationResponse> getAllReservations();
    ReservationResponse cancelReservation(Long reservationId);
    ReservationResponse confirmReservation(Long reservationId);
    ReservationResponse assignTables(Long reservationId, AssignTablesRequest request);
}
