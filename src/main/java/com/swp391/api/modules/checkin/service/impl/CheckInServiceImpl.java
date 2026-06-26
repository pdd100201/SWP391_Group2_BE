package com.swp391.api.modules.checkin.service.impl;

import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.checkin.service.CheckInService;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinates reservation arrival and table occupation updates.
 */
@Service
public class CheckInServiceImpl implements CheckInService {

    private static final List<ReservationStatus> CHECK_IN_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED
    );

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    /**
     * Constructor injection for repositories owned by the reservation and table modules.
     *
     * @param reservationRepository repository for reservation records
     * @param tableRepository repository for restaurant table records
     */
    public CheckInServiceImpl(ReservationRepository reservationRepository,
                              TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    /**
     * Returns all pending or confirmed reservations for one date, optionally filtered by guest keyword.
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
     * Validates and executes the check-in flow atomically.
     *
     * <p>The reservation must be {@code PENDING} or {@code CONFIRMED}; the target table must be
     * active and {@code AVAILABLE}. On success the reservation becomes {@code ARRIVED}, the
     * reservation is linked to {@code table_id}, and the table becomes {@code OCCUPIED}.</p>
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

        if (table.getStatus() != RestaurantTable.TableStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table is not available");
        }

        reservation.setStatus(ReservationStatus.ARRIVED);
        reservation.setTableId(table.getId());
        table.setStatus(RestaurantTable.TableStatus.OCCUPIED);

        tableRepository.save(table);
        Reservation savedReservation = reservationRepository.save(reservation);
        return toResponse(savedReservation);
    }

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

    @Override
    public com.swp391.api.modules.checkin.dto.ActiveGuestResponse getActiveGuestByTable(Long tableId) {
        // 🚀 ĐỔI SANG HÀM MỚI TẠO Ở REPOSITORY ĐỂ TÌM QUA BẢNG TRUNG GIAN
        java.util.Optional<com.swp391.api.modules.reservation.entity.Reservation> reservationOpt = reservationRepository
                .findActiveReservationByTableId(tableId);

        if (reservationOpt.isEmpty()) {
            return null;
        }

        com.swp391.api.modules.reservation.entity.Reservation reservation = reservationOpt.get();

        return com.swp391.api.modules.checkin.dto.ActiveGuestResponse.builder()
                .fullName(reservation.getFullName())
                .phone(reservation.getPhone())
                .numberOfGuests(reservation.getNumberOfGuests())
                .checkInTime(reservation.getReservationTime().toString())
                .orderId("ORD-" + tableId)
                .build();
    }
}
