package com.swp391.api.modules.reservation.controller;

import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @PostMapping("/walk-in")
    public ResponseEntity<ReservationResponse> createWalkInReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createWalkInReservation(request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations() {
        return ResponseEntity.ok(reservationService.getMyReservations());
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(reservationService.cancelReservation(reservationId));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(reservationService.confirmReservation(reservationId));
    }

    @PatchMapping("/{id}/assign-tables")
    public ResponseEntity<ReservationResponse> assignTables(
            @PathVariable("id") Long reservationId,
            @Valid @RequestBody com.swp391.api.modules.reservation.dto.AssignTablesRequest request) {
        return ResponseEntity.ok(reservationService.assignTables(reservationId, request));
    }

    @PatchMapping("/{id}/change-tables")
    public ResponseEntity<ReservationResponse> changeTables(
            @PathVariable("id") Long reservationId,
            @Valid @RequestBody com.swp391.api.modules.reservation.dto.AssignTablesRequest request) {
        return ResponseEntity.ok(reservationService.changeTables(reservationId, request));
    }
}
