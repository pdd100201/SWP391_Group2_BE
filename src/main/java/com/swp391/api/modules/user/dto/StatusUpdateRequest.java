package com.swp391.api.modules.user.dto;

import com.swp391.api.modules.user.entity.User;

public class StatusUpdateRequest {

    private User.Status status;

    public User.Status getStatus() {
        return status;
    }

    public void setStatus(User.Status status) {
        this.status = status;
    }
}
