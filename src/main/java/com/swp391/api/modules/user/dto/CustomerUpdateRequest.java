package com.swp391.api.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public class CustomerUpdateRequest {

    private String fullName;

    @Email(message = "Email must be valid")
    private String customersEmail;

    @Pattern(regexp = "^[0-9]{9,11}$", message = "Phone number must contain 9 to 11 digits")
    private String phone;

    private String avatarUrl;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCustomersEmail() {
        return customersEmail;
    }

    public void setCustomersEmail(String customersEmail) {
        this.customersEmail = customersEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
