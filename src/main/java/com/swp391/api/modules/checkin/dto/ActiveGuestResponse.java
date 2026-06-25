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
    private String fullName;
    private String phone;
    private int numberOfGuests;
    private String checkInTime;
    private String orderId;
}