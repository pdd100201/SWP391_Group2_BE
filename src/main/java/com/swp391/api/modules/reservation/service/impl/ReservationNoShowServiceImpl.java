package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationNoShowService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dịch vụ xử lý tự động hủy các đơn đặt bàn quá giờ hẹn (No-Show).
 * 
 * <p>Quy trình hoạt động ngầm (Scheduler):</p>
 * 1. Quét tìm các đơn đặt bàn ở trạng thái CONFIRMED đã quá giờ hẹn 15 phút mà khách chưa Check-in.
 * 2. Cập nhật trạng thái đơn đặt bàn sang NO_SHOW.
 * 3. Trả toàn bộ các bàn đang bị khóa (RESERVED) của đơn đó quay về trạng thái trống (AVAILABLE).
 */
@Service
public class ReservationNoShowServiceImpl implements ReservationNoShowService {

    /**
     * Thời gian gia hạn tối đa cho phép khách đến muộn (15 phút).
     * Quá 15 phút so với giờ hẹn đặt bàn, hệ thống tự động đánh dấu NO_SHOW.
     */
    private static final int NO_SHOW_GRACE_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    public ReservationNoShowServiceImpl(ReservationRepository reservationRepository,
                                        TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    /**
     * Quét và đánh dấu NO_SHOW cho các đơn đặt bàn quá hạn 15 phút, đồng thời giải phóng bàn bị giữ.
     * Hàm này được gọi định kỳ từ Scheduler ngầm.
     */
    @Override
    @Transactional
    public void markNoShowsAndReleaseTables() {
        LocalDateTime cutoffDateTime = LocalDateTime.now().minusMinutes(NO_SHOW_GRACE_MINUTES);
        List<Reservation> reservations = reservationRepository.findConfirmedNoShowCandidatesWithTablesBefore(
                cutoffDateTime.toLocalDate(),
                cutoffDateTime.toLocalTime()
        );

        for (Reservation reservation : reservations) {
            reservation.setStatus(ReservationStatus.NO_SHOW);
            releaseReservedTables(reservation);
        }

        reservationRepository.saveAll(reservations);
    }

    /**
     * Giải phóng các bàn ăn đang bị giữ chỗ (RESERVED) của đơn NO_SHOW quay trở về trạng thái trống (AVAILABLE).
     * 
     * @param reservation Đơn đặt bàn quá hạn bị hủy NO_SHOW
     */
    private void releaseReservedTables(Reservation reservation) {
        List<RestaurantTable> tables = new java.util.ArrayList<>();
        if (reservation.getTables() != null) {
            tables.addAll(reservation.getTables());
        }
        if (tables.isEmpty() && reservation.getTableId() != null) {
            tableRepository.findById(reservation.getTableId()).ifPresent(tables::add);
        }
        if (tables.isEmpty()) {
            return;
        }

        tables.stream()
                .filter(table -> table.getStatus() == RestaurantTable.TableStatus.RESERVED)
                .forEach(table -> table.setStatus(RestaurantTable.TableStatus.AVAILABLE));
        tableRepository.saveAll(tables);
    }
}
