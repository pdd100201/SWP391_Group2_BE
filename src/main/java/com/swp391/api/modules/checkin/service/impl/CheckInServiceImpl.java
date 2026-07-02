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

    // Khoi tao service voi cac repository can dung cho luong check-in, ban va order.
    public CheckInServiceImpl(ReservationRepository reservationRepository,
                              TableRepository tableRepository,
                              OrderRepository orderRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    // Lay danh sach dat ban co the check-in trong ngay, co the loc theo tu khoa tim kiem.
    public List<ReservationResponse> getCheckInReservations(LocalDate date, String search) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation date is required");
        }

        String keyword = search == null ? "" : search.trim();
        return reservationRepository.findCheckInCandidates(date, keyword, CHECK_IN_STATUSES).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    // Xu ly check-in: kiem tra reservation, kiem tra ban, gan ban va chuyen trang thai sang ARRIVED.
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

    @Override
    @Transactional(readOnly = true)
    // Lay thong tin khach dang dung ban da check-in theo tableId, kem order dang lien ket neu co.
    public ActiveGuestResponse getActiveGuestByTable(Long tableId) {
        return reservationRepository.findActiveReservationByTableId(tableId)
                .map(this::toActiveGuestResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    // Lay thong tin khach da duoc giu ban nhung chua check-in theo tableId.
    public ActiveGuestResponse getReservedGuestByTable(Long tableId) {
        return reservationRepository.findReservedReservationByTableId(tableId)
                .map(this::toReservedGuestResponse)
                .orElse(null);
    }

    // Kiem tra ban da duoc lien ket voi reservation qua tableId chinh hoac danh sach tables hay chua.
    private boolean isTableLinkedToReservation(Reservation reservation, RestaurantTable table) {
        if (reservation.getTableId() != null && reservation.getTableId().equals(table.getId())) {
            return true;
        }

        return reservation.getTables() != null
                && reservation.getTables().stream().anyMatch(currentTable -> currentTable.getId().equals(table.getId()));
    }

    // Chuyen reservation da check-in thanh response cho active guest va bo sung thong tin order neu ton tai.
    private ActiveGuestResponse toActiveGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = baseGuestResponse(reservation);
        Optional<RestaurantOrder> orderOpt =
                orderRepository.findByReservationReservationId(reservation.getReservationId());

        orderOpt.ifPresent(order -> {
            response.setOrderId(order.getId());
            response.setOrderCode(order.getOrderCode());
            response.setOrderPath("/dashboard/orders-service");
        });

        return response;
    }

    // Chuyen reservation dang reserved thanh response cho guest dang giu ban.
    private ActiveGuestResponse toReservedGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = baseGuestResponse(reservation);
        response.setOrderCode("RES-" + reservation.getReservationId());
        return response;
    }

    // Tao response co cac thong tin khach chung duoc dung lai cho active guest va reserved guest.
    private ActiveGuestResponse baseGuestResponse(Reservation reservation) {
        ActiveGuestResponse response = new ActiveGuestResponse();
        response.setReservationId(reservation.getReservationId());
        response.setFullName(reservation.getFullName());
        response.setPhone(reservation.getPhone());
        response.setNumberOfGuests(reservation.getNumberOfGuests());
        response.setCheckInTime(reservation.getReservationTime().toString());
        return response;
    }

    // Chuyen entity Reservation thanh DTO ReservationResponse de tra ve cho controller.
    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
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
    }
}
