package com.swp391.api.modules.payment.dto;

import com.swp391.api.modules.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class PaymentRequest {
    @NotNull
    private PaymentMethod paymentMethod;

    private String bankCode;
    private String note;

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
