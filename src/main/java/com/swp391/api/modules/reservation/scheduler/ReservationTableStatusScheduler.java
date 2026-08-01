package com.swp391.api.modules.reservation.scheduler;

import com.swp391.api.modules.reservation.service.ReservationTableStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ====================================================================================
 * BẢN HƯỚNG DẪN HOẠT ĐỘNG SPRING BOOT SCHEDULER (TÁC VỤ TỰ ĐỘNG CHẠY NGẦM)
 * ====================================================================================
 * CÁCH SPRING BOOT TỰ ĐỘNG CHẠY MÀ KHÔNG CẦN FRONTEND (REACT) GỌI:
 * 
 * 1. @Component: Đánh dấu class này là một "Bean" được Spring Boot tự động phát hiện và quản lý.
 * 2. @Scheduled(fixedDelay = 60000): Spring Boot tạo một Thread chạy ngầm. 
 *    Cứ mỗi 60.000 ms (1 phút), nó tự động gọi hàm reserveTablesBeforeReservationTime().
 * 
 * QUY TRÌNH XỬ LÝ THEO TỪNG BƯỚC (STEP-BY-STEP FLOW):
 *  - Bước 1: Scheduler thức dậy sau mỗi 1 phút.
 *  - Bước 2: Gọi Service (reservationTableStatusService).
 *  - Bước 3: Service quét Database (MySQL) tìm các đơn đặt bàn CONFIRMED sắp đến giờ hẹn.
 *  - Bước 4: Tự động đổi trạng thái bàn từ AVAILABLE (Trống) sang RESERVED (Đã giữ chỗ).
 *  - Bước 5: Ghi Log kết quả và tiếp tục ngủ 1 phút tiếp theo.
 * ====================================================================================
 */
@Component
public class ReservationTableStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationTableStatusScheduler.class);

    private final ReservationTableStatusService reservationTableStatusService;

    // Dependency Injection: Spring tự động tiêm Service xử lý logic vào đây
    public ReservationTableStatusScheduler(ReservationTableStatusService reservationTableStatusService) {
        this.reservationTableStatusService = reservationTableStatusService;
    }

    /**
     * Tác vụ tự động quét và khóa bàn trước giờ hẹn khách đặt
     * - fixedDelay = 60_000: Khoảng cách giữa các lần chạy là 60 giây (1 phút)
     * - initialDelay = 10_000: Chờ 10 giây sau khi ứng dụng Spring Boot khởi động xong mới bắt đầu chạy
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void reserveTablesBeforeReservationTime() {
        try {
            // Bước 1: Kích hoạt Service kiểm tra và tự động khóa bàn trong Database
            reservationTableStatusService.reserveTablesForUpcomingConfirmedReservations();
        } catch (Exception ex) {
            // Bước 2: Bắt lỗi nếu có sự cố xảy ra để tránh làm đứt đoạn tiến trình ngầm
            log.error("Lỗi tự động giữ chỗ bàn ăn cho các đơn đặt sắp tới: ", ex);
        }
    }
}
