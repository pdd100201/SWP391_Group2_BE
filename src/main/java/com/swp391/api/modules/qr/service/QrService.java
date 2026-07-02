package com.swp391.api.modules.qr.service;

import com.swp391.api.modules.qr.dto.QrMenuResponse;
import com.swp391.api.modules.qr.dto.QrOrderRequest;
import com.swp391.api.modules.qr.dto.QrOrderResponse;
import com.swp391.api.modules.qr.dto.QrSessionResponse;

public interface QrService {

    QrSessionResponse createSession(Long tableId);

    QrMenuResponse getMenu();

    QrOrderResponse createOrder(QrOrderRequest request);

    QrOrderResponse getOrderStatus(Long orderId);
}
