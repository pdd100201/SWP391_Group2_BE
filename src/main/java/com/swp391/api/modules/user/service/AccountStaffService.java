package com.swp391.api.modules.user.service;

import com.swp391.api.modules.user.dto.PageResponse;
import com.swp391.api.modules.user.dto.StaffRequest;
import com.swp391.api.modules.user.dto.StaffResponse;
import com.swp391.api.modules.user.dto.StatusUpdateRequest;
import com.swp391.api.modules.user.entity.User;

public interface AccountStaffService {

    /**
     * Get paginated list of staff accounts with optional filters.
     */
    PageResponse<StaffResponse> getStaffList(String keyword, User.Role role,
                                             User.Status status, int page, int size);

    /**
     * Get detail of a single staff by userId.
     */
    StaffResponse getStaffById(Long id);

    /**
     * Create a new staff account.
     */
    StaffResponse createStaff(StaffRequest request);

    /**
     * Update staff information.
     */
    StaffResponse updateStaff(Long id, StaffRequest request);

    /**
     * Toggle ACTIVE/DEACTIVE status.
     */
    StaffResponse updateStaffStatus(Long id, StatusUpdateRequest request);

    /**
     * Soft-delete a staff account by setting status to DEACTIVE.
     */
    void deleteStaff(Long id);
}
