package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.common.exception.BusinessException;
import com.swp391.api.modules.reservation.dto.AssignTablesRequest;
import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationService;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST");
    private static final int RESERVATION_SLOT_MINUTES = 90;
    private static final int MAX_BOOKABLE_SEATS = 48;

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final TableRepository tableRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  CustomerRepository customerRepository,
                                  TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
    }

    @Override
    public ReservationResponse createReservation(CreateReservationRequest request) {
        String currentEmail = getCurrentEmailRequired();
        Customer customer = customerRepository.findByCustomersEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers can create reservations"));

        LocalDateTime reservationDateTime = LocalDateTime.of(request.getReservationDate(), request.getReservationTime());
        if (reservationDateTime.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation date and time must be in the future");
        }

        if (request.getNumberOfGuests() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number of guests must not exceed 30");
        }

        validateTableAvailability(request);

        Reservation reservation = new Reservation();
        reservation.setFullName(request.getFullName());
        reservation.setPhone(request.getPhone());
        reservation.setEmail(request.getEmail());
        reservation.setReservationDate(request.getReservationDate());
        reservation.setReservationTime(request.getReservationTime());
        reservation.setNumberOfGuests(request.getNumberOfGuests());
        reservation.setNote(request.getNote());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCustomerId(customer.getCustomerId());

        return toResponse(reservationRepository.save(reservation));
    }

    private void validateTableAvailability(CreateReservationRequest request) {
        LocalTime startTime = request.getReservationTime();
        LocalTime endTime = startTime.plusMinutes(RESERVATION_SLOT_MINUTES);
        LocalTime overlapStart = startTime.minusMinutes(RESERVATION_SLOT_MINUTES);
        Integer requestedGuests = request.getNumberOfGuests();

        if (requestedGuests <= 6) {
            Long totalFitTables = tableRepository.countAvailableTablesThatFitGuests(requestedGuests);
            Long currentActiveReservationsCount = reservationRepository.countActiveReservationsOverlappingWindow(
                    request.getReservationDate(),
                    overlapStart,
                    endTime
            );

            if (currentActiveReservationsCount >= totalFitTables) {
                throw new BusinessException("Nhà hàng đã hết bàn phù hợp cho số lượng khách này vào khung giờ đã chọn. Vui lòng chọn khung giờ khác!");
            }
            return;
        }

        Long totalRestaurantSeats = tableRepository.sumTotalRestaurantSeats();
        Long totalCurrentBookedSeats = reservationRepository.sumActiveBookedSeatsOverlappingWindow(
                request.getReservationDate(),
                overlapStart,
                endTime
        );

        if (totalCurrentBookedSeats + requestedGuests > totalRestaurantSeats) {
            throw new BusinessException("Nhà hàng không đủ tổng sức chứa cho đoàn khách đông vào khung giờ này. Vui lòng chọn khung giờ khác!");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations() {
        String email = getCurrentEmailRequired();
        return reservationRepository.findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(email).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        requireAnyRole(STAFF_ROLES);
        return reservationRepository.findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() == ReservationStatus.COMPLETED || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation cannot be cancelled");
        }

        if (!hasAnyRole(STAFF_ROLES)) {
            String email = getCurrentEmailRequired();
            if (!reservation.getEmail().equalsIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this reservation");
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    public ReservationResponse confirmReservation(Long reservationId) {
        requireAnyRole(STAFF_ROLES);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending reservations can be confirmed");
        }

        LocalTime reservationTime = reservation.getReservationTime();
        LocalTime overlapStart = reservationTime.minusMinutes(RESERVATION_SLOT_MINUTES);
        LocalTime overlapEnd = reservationTime.plusMinutes(RESERVATION_SLOT_MINUTES);
        Long currentBookedSeats = reservationRepository.sumOccupiedSeatsForOverlappingWindow(
                reservation.getReservationDate(),
                overlapStart,
                overlapEnd
        );

        if (currentBookedSeats + reservation.getNumberOfGuests() > MAX_BOOKABLE_SEATS) {
            throw new BusinessException("Cannot confirm: The restaurant capacity is full for this 90-minute time slot.");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservationRepository.save(reservation));
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

    private Optional<String> getCurrentEmailOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || authentication.getName() == null) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }

    private String getCurrentEmailRequired() {
        return getCurrentEmailOptional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private void requireAnyRole(Set<String> roles) {
        if (!hasAnyRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private boolean hasAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(roles::contains);
    }

    @Override
    public ReservationResponse assignTables(Long reservationId, AssignTablesRequest request) {
        // 1. Chỉ cho phép nhân viên thao tác
        requireAnyRole(STAFF_ROLES);

        // 2. Tìm đơn đặt bàn
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException("Không thể gán bàn cho đơn đã bị hủy!");
        }

        // 3. Tìm danh sách các bàn dựa trên các ID gửi lên
        List<com.swp391.api.modules.table.entity.RestaurantTable> selectedTables = tableRepository.findAllById(request.getTableIds());

        if (selectedTables.isEmpty()) {
            throw new BusinessException("Danh sách bàn không hợp lệ!");
        }

        // 4. Kiểm tra xem các bàn chọn có đang trống không (tránh nhân viên thao tác nhầm)
        for (com.swp391.api.modules.table.entity.RestaurantTable table : selectedTables) {
            if (table.getStatus() != com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.AVAILABLE) {
                throw new BusinessException("Bàn " + table.getTableNumber() + " không khả dụng!");
            }
        }

        // 5. Kiểm tra sức chứa (Tùy chọn: Có thể tắt đi nếu muốn ép khách ngồi chật)
        int totalCapacity = selectedTables.stream().mapToInt(com.swp391.api.modules.table.entity.RestaurantTable::getCapacity).sum();
        if (totalCapacity < reservation.getNumberOfGuests()) {
            throw new BusinessException("Tổng sức chứa (" + totalCapacity + ") không đủ cho " + reservation.getNumberOfGuests() + " khách!");
        }

        // 6. Cập nhật dữ liệu
        reservation.setTables(selectedTables);
        reservation.setStatus(ReservationStatus.ARRIVED); // Hoặc trạng thái CHECKED_IN tùy ông định nghĩa

        // 7. Chuyển trạng thái tất cả các bàn sang OCCUPIED (Đỏ)
        for (com.swp391.api.modules.table.entity.RestaurantTable table : selectedTables) {
            table.setStatus(com.swp391.api.modules.table.entity.RestaurantTable.TableStatus.OCCUPIED);
        }
        tableRepository.saveAll(selectedTables);

        return toResponse(reservationRepository.save(reservation));
    }
}
