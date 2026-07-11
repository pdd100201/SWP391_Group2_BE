package com.swp391.api.modules.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayWebhookRequest {
    private String id;
    private String gateway;
    private String transactionDate;
    private String accountNumber;
    private String subAccount;
    private String code;
    private String content;
    private String transferType;
    private String description;
    private BigDecimal transferAmount;
    private BigDecimal accumulated;
    private String referenceCode;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getSubAccount() { return subAccount; }
    public void setSubAccount(String subAccount) { this.subAccount = subAccount; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getTransferAmount() { return transferAmount; }
    public void setTransferAmount(BigDecimal transferAmount) { this.transferAmount = transferAmount; }
    public BigDecimal getAccumulated() { return accumulated; }
    public void setAccumulated(BigDecimal accumulated) { this.accumulated = accumulated; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
}
