package com.swp391.api.modules.reservation.dto;

import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ReservationResponse {

    private Long reservationId;
    private Long customerId;
    private Long tableId;
    private List<Long> tableIds = new ArrayList<>();
    private String fullName;
    private String phone;
    private String email;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private Integer numberOfGuests;
    private String note;
    private ReservationStatus status;
    private Long orderId;
    private String orderCode;
    private OrderStatus orderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReservationResponse() {
    }

    public ReservationResponse(Long reservationId,
                               Long customerId,
                               Long tableId,
                               String fullName,
                               String phone,
                               String email,
                               LocalDate reservationDate,
                               LocalTime reservationTime,
                               Integer numberOfGuests,
                               String note,
                               ReservationStatus status,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.tableId = tableId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.reservationDate = reservationDate;
        this.reservationTime = reservationTime;
        this.numberOfGuests = numberOfGuests;
        this.note = note;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public List<Long> getTableIds() {
        return tableIds;
    }

    public void setTableIds(List<Long> tableIds) {
        this.tableIds = tableIds;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public LocalTime getReservationTime() {
        return reservationTime;
    }

    public void setReservationTime(LocalTime reservationTime) {
        this.reservationTime = reservationTime;
    }

    public Integer getNumberOfGuests() {
        return numberOfGuests;
    }

    public void setNumberOfGuests(Integer numberOfGuests) {
        this.numberOfGuests = numberOfGuests;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
