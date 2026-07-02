package com.swp391.api.modules.checkin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body used to assign a reservation to an available restaurant table.
 */
public class CheckInRequest {

    /**
     * Identifier of the reservation that will be checked in.
     */
    @NotNull(message = "Reservation id is required")
    private Long reservationId;

    /**
     * Identifier of the table that will be assigned to the reservation.
     */
    @NotNull(message = "Table id is required")
    private Long tableId;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }
}
