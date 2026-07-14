package com.swp391.api.modules.qr.dto;

public class QrAccessTokenResponse {
    private String publicAccessToken;

    public QrAccessTokenResponse(String publicAccessToken) {
        this.publicAccessToken = publicAccessToken;
    }

    public String getPublicAccessToken() { return publicAccessToken; }
    public void setPublicAccessToken(String v) { this.publicAccessToken = v; }
}
