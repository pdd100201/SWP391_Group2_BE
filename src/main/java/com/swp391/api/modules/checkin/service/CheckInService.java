package com.swp391.api.modules.checkin.service;

import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.checkin.dto.ActiveGuestResponse; // 1. Khai báo địa chỉ ở đây
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for the restaurant check-in flow.
 */
public interface CheckInService {

    /**
     * Finds reservations that can still be checked in for the selected date.
     */
    List<ReservationResponse> getCheckInReservations(LocalDate date, String search);

    /**
     * Assigns an available table to a valid reservation in one transaction.
     */
    ReservationResponse processCheckInTransaction(CheckInRequest request);

    /**
     * 2. Gọi tên ngắn gọn ở đây là xong
     * Lấy thông tin khách hàng đang ngồi thực tế tại bàn dựa trên tableId
     */
    ActiveGuestResponse getActiveGuestByTable(Long tableId);
}