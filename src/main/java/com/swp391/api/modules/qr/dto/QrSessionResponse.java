package com.swp391.api.modules.qr.dto;

import java.time.LocalDateTime;

public class QrSessionResponse {

    private String sessionToken;
    private Long tableId;
    private String tableNumber;
    private LocalDateTime expiredAt;

    public QrSessionResponse() {}

    public QrSessionResponse(String sessionToken, Long tableId, String tableNumber, LocalDateTime expiredAt) {
        this.sessionToken = sessionToken;
        this.tableId = tableId;
        this.tableNumber = tableNumber;
        this.expiredAt = expiredAt;
    }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }

    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
}
