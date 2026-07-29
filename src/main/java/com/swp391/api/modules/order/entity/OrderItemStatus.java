package com.swp391.api.modules.order.entity;

// Workflow state of each dish line from draft to kitchen/service completion.
public enum OrderItemStatus {
    DRAFT,
    CONFIRMED,
    PREPARING,
    READY,
    SERVED,
    CANCELLED,
    VOIDED
}
