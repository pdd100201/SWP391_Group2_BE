package com.swp391.api.modules.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AssignTablesRequest {

    @NotEmpty(message = "Please select at least one table.")
    private List<Long> tableIds;

    public List<Long> getTableIds() {
        return tableIds;
    }

    public void setTableIds(List<Long> tableIds) {
        this.tableIds = tableIds;
    }
}
