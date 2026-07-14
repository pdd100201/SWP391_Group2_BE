package com.swp391.api.modules.qr.service;

import com.swp391.api.modules.qr.dto.QrAccessTokenResponse;
import com.swp391.api.modules.qr.dto.QrMenuResponse;
import com.swp391.api.modules.qr.dto.QrOrderRequest;
import com.swp391.api.modules.qr.dto.QrOrderResponse;
import com.swp391.api.modules.qr.dto.QrOrderSummaryResponse;
import com.swp391.api.modules.qr.dto.QrSessionResponse;

import java.util.List;
import java.util.Optional;

public interface QrService {

    QrSessionResponse createSession(Long tableId);

    QrMenuResponse getMenu();

    QrOrderResponse createOrder(QrOrderRequest request);

    QrOrderResponse getOrderStatus(Long orderId);

    Optional<QrOrderResponse> getActiveOrder(Long tableId);

    List<QrOrderSummaryResponse> getAllOrders();

    QrOrderResponse addItem(Long orderId, Long menuItemId, Integer quantity);

    QrOrderResponse updateItem(Long orderId, Long itemId, Integer quantity, String note);

    QrOrderResponse removeItem(Long orderId, Long itemId);

    QrOrderResponse submitOrder(Long orderId);

    QrOrderResponse updateItemStatus(Long orderId, Long itemId, String status);

    QrOrderResponse closeOrder(Long orderId);

    Optional<QrAccessTokenResponse> getAccessTokenForTable(Long tableId);
}
