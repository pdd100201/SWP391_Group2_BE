package com.swp391.api.modules.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveGuestResponse {
    private Long reservationId;
    private String fullName;
    private String phone;
    private int numberOfGuests;
    private String checkInTime;
    private Long orderId;
    private String orderCode;
    private String orderPath;
}
