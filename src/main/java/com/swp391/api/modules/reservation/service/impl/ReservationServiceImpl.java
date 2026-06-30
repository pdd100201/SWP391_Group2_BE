package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.common.exception.BusinessException;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.reservation.dto.AssignTablesRequest;
import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import java.time.LocalDate;
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

    private static final Set<String> RESERVATION_MANAGEMENT_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST");
    private static final Set<String> RESERVATION_VIEW_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST", "ROLE_WAITER");
    private static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_RECEPTIONIST");
    private static final int AVERAGE_DINING_MINUTES = 90;
    private static final int MIN_ADVANCE_BOOKING_HOURS = 2;

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  CustomerRepository customerRepository,
                                  TableRepository tableRepository,
                                  OrderRepository orderRepository) {
        this.reservationRepository = reservationRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public ReservationResponse createReservation(CreateReservationRequest request) {
        String currentEmail = getCurrentEmailRequired();
        Customer customer = customerRepository.findByCustomersEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers can create reservations"));

        LocalDateTime reservationDateTime = LocalDateTime.of(request.getReservationDate(), request.getReservationTime());
        LocalDateTime earliestReservationDateTime = LocalDateTime.now().plusHours(MIN_ADVANCE_BOOKING_HOURS);
        if (reservationDateTime.isBefore(earliestReservationDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservations must be made at least 2 hours in advance");
        }

        validateTableAvailability(
                request.getReservationDate(),
                request.getReservationTime(),
                request.getNumberOfGuests());

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
        requireAnyRole(RESERVATION_VIEW_ROLES);
        return reservationRepository.findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation cannot be cancelled");
        }

        orderRepository.findByReservationReservationId(reservationId)
                .filter(order -> order.getStatus() == OrderStatus.OPEN)
                .ifPresent(order -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Reservation has an open order. Cancel the order from Orders & Service instead"
                    );
                });

        if (!hasAnyRole(RESERVATION_MANAGEMENT_ROLES)) {
            String email = getCurrentEmailRequired();
            if (!reservation.getEmail().equalsIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this reservation");
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        releaseReservedTables(reservation);
        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    public ReservationResponse confirmReservation(Long reservationId) {
        requireAnyRole(RESERVATION_MANAGEMENT_ROLES);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending reservations can be confirmed");
        }

        validateTableAvailability(
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getNumberOfGuests());

        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    public ReservationResponse assignTables(Long reservationId, AssignTablesRequest request) {
        requireAnyRole(STAFF_ROLES);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw new BusinessException("Không thể gán bàn cho đơn đã bị hủy hoặc no-show!");
        }

        List<RestaurantTable> selectedTables = tableRepository.findAllById(request.getTableIds());
        if (selectedTables.size() != request.getTableIds().size()) {
            throw new BusinessException("Danh sách bàn không hợp lệ!");
        }

        for (RestaurantTable table : selectedTables) {
            boolean assignableStatus = table.getStatus() == RestaurantTable.TableStatus.AVAILABLE
                    || table.getStatus() == RestaurantTable.TableStatus.RESERVED;
            if (!Boolean.TRUE.equals(table.getIsActive()) || !assignableStatus) {
                throw new BusinessException("Bàn " + table.getTableNumber() + " không khả dụng!");
            }
        }

        int totalCapacity = selectedTables.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
        if (totalCapacity < reservation.getNumberOfGuests()) {
            throw new BusinessException("Tổng sức chứa (" + totalCapacity + ") không đủ cho "
                    + reservation.getNumberOfGuests() + " khách!");
        }

        reservation.setTables(selectedTables);
        reservation.setStatus(ReservationStatus.ARRIVED);

        selectedTables.forEach(table -> table.setStatus(RestaurantTable.TableStatus.OCCUPIED));
        tableRepository.saveAll(selectedTables);

        return toResponse(reservationRepository.save(reservation));
    }

    private void validateTableAvailability(LocalDate reservationDate,
                                           LocalTime requestedStart,
                                           Integer requestedGuests) {
        LocalTime requestedEnd = requestedStart.plusMinutes(AVERAGE_DINING_MINUTES);
        LocalTime overlapStart = requestedStart.minusMinutes(AVERAGE_DINING_MINUTES);

        long totalActiveCapacity = Optional.ofNullable(tableRepository.sumActiveRestaurantSeats()).orElse(0L);
        long unavailableCapacity = reservationRepository
                .findUnavailableTablesForReservationWindow(reservationDate, overlapStart, requestedEnd)
                .stream()
                .filter(table -> Boolean.TRUE.equals(table.getIsActive()))
                .mapToLong(RestaurantTable::getCapacity)
                .sum();

        long availableCapacity = totalActiveCapacity - unavailableCapacity;
        if (availableCapacity < requestedGuests) {
            throw new BusinessException("Nhà hàng đã hết chỗ trong khung giờ đã chọn. Vui lòng chọn thời gian khác!");
        }
    }

    private void releaseReservedTables(Reservation reservation) {
        List<RestaurantTable> tables = reservation.getTables();
        if (tables == null || tables.isEmpty()) {
            return;
        }

        tables.stream()
                .filter(table -> table.getStatus() == RestaurantTable.TableStatus.RESERVED)
                .forEach(table -> table.setStatus(RestaurantTable.TableStatus.AVAILABLE));
        tableRepository.saveAll(tables);
    }

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
        orderRepository.findByReservationReservationId(reservation.getReservationId()).ifPresent(order -> {
            response.setOrderId(order.getId());
            response.setOrderCode(order.getOrderCode());
            response.setOrderStatus(order.getStatus());
        });
        return response;
    }

    private Optional<String> getCurrentEmailOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null) {
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
}
