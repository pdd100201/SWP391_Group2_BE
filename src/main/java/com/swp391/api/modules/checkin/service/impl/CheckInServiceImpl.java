package com.swp391.api.modules.checkin.service.impl;

import com.swp391.api.modules.checkin.dto.ActiveGuestResponse;
import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.checkin.service.CheckInService;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckInServiceImpl implements CheckInService {

    private static final List<ReservationStatus> CHECK_IN_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED
    );

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;

    /**
     * Khởi tạo service với các repository cần dùng cho luồng check-in, bàn và order.
     */
    public CheckInServiceImpl(ReservationRepository reservationRepository,
                              TableRepository tableRepository,
                              OrderRepository orderRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Lấy danh sách các đơn đặt bàn có thể thực hiện Check-in trong ngày
     *
     * <p>Chỉ tìm các đơn ở trạng thái PENDING hoặc CONFIRMED.</p>
     *
     * @param date Ngày check-in cần tra cứu
     * @param search Từ khóa tìm kiếm (Tên khách hoặc Số điện thoại)
     * @return Danh sách các đơn đặt bàn sẵn sàng check-in
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getCheckInReservations(LocalDate date, String search) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation date is required");
        }

        String keyword = search == null ? "" : search.trim();
        return reservationRepository.findCheckInCandidates(date, keyword, CHECK_IN_STATUSES).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Xử lý thủ tục Check-in đón khách và gán bàn ăn thực tế
     *
     * <p>Quy trình thực thi:</p>
     * 1. Kiểm tra đơn đặt bàn hợp lệ (phải ở trạng thái PENDING hoặc CONFIRMED).
     * 2. Kiểm tra bàn chọn có đang trống (AVAILABLE) hoặc đang được khóa giữ chỗ cho chính đơn này (RESERVED).
     * 3. Chuyển trạng thái đơn sang ARRIVED.
     * 4. Chuyển trạng thái bàn sang OCCUPIED (Đang phục vụ / Hiển thị màu Đỏ).
     *
     * @param request Thông tin yêu cầu check-in (ID đơn đặt bàn + ID bàn ăn)
     * @return Thông tin đơn đặt bàn sau khi check-in thành công
     */
    @Override
    @Transactional
    public ReservationResponse processCheckInTransaction(CheckInRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (!CHECK_IN_STATUSES.contains(reservation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending or confirmed reservations can be checked in"
            );
        }

        RestaurantTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));

        if (!Boolean.TRUE.equals(table.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table is not active");
        }

        boolean tableReservedForThisReservation = table.getStatus() == RestaurantTable.TableStatus.RESERVED
                && isTableLinkedToReservation(reservation, table);
        // Chỉ cho phép dùng bàn AVAILABLE hoặc bàn RESERVED đang được giữ cho chính reservation này.
        if (table.getStatus() != RestaurantTable.TableStatus.AVAILABLE && !tableReservedForThisReservation) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table is not available");
        }

        reservation.setStatus(ReservationStatus.ARRIVED);
        reservation.setTableId(table.getId());
        if (!isTableLinkedToReservation(reservation, table)) {
            reservation.getTables().add(table);
        }
        table.setStatus(RestaurantTable.TableStatus.OCCUPIED);

        tableRepository.save(table);
        return toResponse(reservationRepository.save(reservation));
    }

    /**
     * Lấy thông tin đoàn khách ĐANG NGỒI ĂN tại bàn (Trạng thái OCCUPIED)
     *
     * @param tableId Mã ID của bàn ăn
     * @return Thông tin khách hàng và mã đơn hàng Order đi kèm
     */
    @Override
    @Transactional(readOnly = true)
    public ActiveGuestResponse getActiveGuestByTable(Long tableId) {
        return reservationRepository.findActiveReservationByTableId(tableId)
                .map(this::toActiveGuestResponse)
                .orElse(null);
    }

    /**
     * Lấy thông tin đoàn khách ĐÃ ĐẶT GIỮ CHỖ TRƯỚC tại bàn nhưng chưa đến (Trạng thái RESERVED)
     *
     * @param tableId Mã ID của bàn ăn
     * @return Thông tin khách hàng và giờ hẹn dự kiến
     */
    @Override
    @Transactional(readOnly = true)
    public ActiveGuestResponse getReservedGuestByTable(Long tableId) {
        return reservationRepository.findReservedReservationByTableId(tableId)
                .map(this::toReservedGuestResponse)
                .orElse(null);
    }

    /**
     * Kiểm tra bàn đã được liên kết với reservation hay chưa.
     *
     * <p>Hỗ trợ cả trường {@code tableId} chính và danh sách {@code tables}
     * để tương thích với các luồng dữ liệu hiện có.</p>
     */
    private boolean isTableLinkedToReservation(Reservation reservation, RestaurantTable table) {
        if (reservation.getTableId() != null && reservation.getTableId().equals(table.getId())) {
            return true;
        }

        return reservation.getTables() != null
                && reservation.getTables().stream().anyMatch(currentTable -> currentTable.getId().equals(table.getId()));
    }

    /**
     * Chuyển reservation đã check-in thành response cho active guest
     * và bổ sung thông tin order nếu tồn tại.
     */
    private ActiveGuestResponse toActiveGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = baseGuestResponse(reservation);
        Optional<RestaurantOrder> orderOpt = findDisplayOrderForReservation(reservation.getReservationId());

        orderOpt.ifPresent(order -> {
            response.setOrderId(order.getId());
            response.setOrderCode(order.getOrderCode());
            response.setOrderPath("/dashboard/orders-service");
        });

        return response;
    }

    private Optional<RestaurantOrder> findDisplayOrderForReservation(Long reservationId) {
        List<RestaurantOrder> orders = orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(reservationId);
        return orders.stream()
                .filter(order -> order.getOrderCode() == null || !order.getOrderCode().startsWith("ORD-MIG-"))
                .findFirst()
                .or(() -> orders.stream().findFirst());
    }

    /**
     * Chuyển reservation đang RESERVED thành response cho khách đang được giữ bàn.
     */
    private ActiveGuestResponse toReservedGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = baseGuestResponse(reservation);
        response.setOrderCode("RES-" + reservation.getReservationId());
        return response;
    }

    /**
     * Tạo response chứa các thông tin khách chung,
     * được dùng lại cho cả active guest và reserved guest.
     */
    private ActiveGuestResponse baseGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = new ActiveGuestResponse();
        response.setReservationId(reservation.getReservationId());
        response.setFullName(reservation.getFullName());
        response.setPhone(reservation.getPhone());
        response.setNumberOfGuests(reservation.getNumberOfGuests());
        response.setCheckInTime(reservation.getReservationTime().toString());
        return response;
    }

    /**
     * Chuyển entity {@link Reservation} thành DTO {@link ReservationResponse}
     * để trả về cho controller.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        ReservationResponse response = new ReservationResponse(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getTableId(),
                reservation.getFullName(),
                reservation.getPhone(),
                reservation.getEmail(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getNumberOfGuests(),
                reservation.getNote(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
        response.setTableIds(resolveTableIds(reservation));
        return response;
    }

    private List<Long> resolveTableIds(Reservation reservation) {
        List<RestaurantTable> tables = reservation.getTables();
        if (tables != null && !tables.isEmpty()) {
            return tables.stream()
                    .map(RestaurantTable::getId)
                    .toList();
        }

        Long tableId = reservation.getTableId();
        return tableId == null ? List.of() : List.of(tableId);
    }
}
