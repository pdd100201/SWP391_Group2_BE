package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationService;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import java.time.LocalDateTime;
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

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  CustomerRepository customerRepository) {
        this.reservationRepository = reservationRepository;
        this.customerRepository = customerRepository;
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
}
