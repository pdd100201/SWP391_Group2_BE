package com.swp391.api.modules.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.sepay")
public class SepayProperties {
    private String bankCode = "";
    private String accountNumber = "";
    private String accountName = "";
    private String webhookApiKey = "";
    private String transferPrefix = "GS";
    private String vietqrTemplate = "compact2";

    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getWebhookApiKey() { return webhookApiKey; }
    public void setWebhookApiKey(String webhookApiKey) { this.webhookApiKey = webhookApiKey; }
    public String getTransferPrefix() { return transferPrefix; }
    public void setTransferPrefix(String transferPrefix) { this.transferPrefix = transferPrefix; }
    public String getVietqrTemplate() { return vietqrTemplate; }
    public void setVietqrTemplate(String vietqrTemplate) { this.vietqrTemplate = vietqrTemplate; }
}
