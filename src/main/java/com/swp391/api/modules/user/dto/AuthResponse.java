package com.swp391.api.modules.user.dto;

public class AuthResponse {
    private String token;
    private String role;
    private String fullName;
    private String email;

    public AuthResponse(String token, String role, String fullName, String email) {
        this.token = token;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public String getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }
}
