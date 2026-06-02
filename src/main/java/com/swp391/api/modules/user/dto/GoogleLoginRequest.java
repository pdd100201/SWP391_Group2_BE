package com.swp391.api.modules.user.dto;

public class GoogleLoginRequest {
    private String credentialToken;

    public String getCredentialToken() {
        return credentialToken;
    }

    public void setCredentialToken(String credentialToken) {
        this.credentialToken = credentialToken;
    }
}
