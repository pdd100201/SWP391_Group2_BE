package com.swp391.api.modules.account.service;

import com.swp391.api.modules.account.dto.StaffRequest;
import com.swp391.api.modules.account.dto.StaffResponse;
import com.swp391.api.modules.account.dto.StatusUpdateRequest;

import java.util.List;

public interface AccountStaffService {

    List<StaffResponse> getStaffList(String keyword, String role, Boolean isActive);

    StaffResponse getStaffById(Long id);

    StaffResponse createStaff(StaffRequest request);

    StaffResponse updateStaff(Long id, StaffRequest request);

    StaffResponse updateStaffStatus(Long id, StatusUpdateRequest request);

    void deleteStaff(Long id);
}
