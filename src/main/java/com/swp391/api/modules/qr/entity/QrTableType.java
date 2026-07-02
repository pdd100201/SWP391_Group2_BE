package com.swp391.api.modules.qr.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "table_types")
public class QrTableType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "table_type_id")
    private Long tableTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "status")
    private String status;

    public Long getTableTypeId() { return tableTypeId; }
    public void setTableTypeId(Long tableTypeId) { this.tableTypeId = tableTypeId; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
