package com.swp391.api.modules.table.service.impl;

import com.swp391.api.modules.table.dto.TableRequest;
import com.swp391.api.modules.table.dto.TableResponse;
import com.swp391.api.modules.table.dto.UpdateTableStatusRequest;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.entity.TableType;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.table.repository.TableTypeRepository;
import com.swp391.api.modules.table.service.TableService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation của {@link TableService}.
 * 
 * <p>Chứa logic nghiệp vụ để quản lý bàn nhà hàng:
 * - Tạo/cập nhật/xóa bàn
 * - Cập nhật trạng thái bàn
 * - Tìm kiếm bàn theo điều kiện khác nhau
 * </p>
 */
@Service
@Transactional
public class TableServiceImpl implements TableService {

    private final TableRepository tableRepository;
    private final TableTypeRepository tableTypeRepository;

    /**
     * Constructor injection cho {@link TableRepository}.
     * 
     * @param tableRepository Repository quản lý bàn
     */
    public TableServiceImpl(TableRepository tableRepository, TableTypeRepository tableTypeRepository) {
        this.tableRepository = tableRepository;
        this.tableTypeRepository = tableTypeRepository;
    }

    /**
     * Chuyển đổi entity {@link RestaurantTable} sang DTO {@link TableResponse}.
     */
    private TableResponse toResponse(RestaurantTable table) {
        TableType tableType = table.getTableType();
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getTableName(),
            tableType != null ? tableType.getId() : null,
            tableType != null ? tableType.getTypeName() : null,
            table.getCapacity(),
            table.getStatus().name(),
            table.getQrCode(),
            table.getIsActive(),
            table.getCreatedAt(),
            table.getUpdatedAt()
        );
    }

    private TableType resolveTableType(TableRequest request) {
        if (request.getTableTypeId() != null) {
            return tableTypeRepository.findById(request.getTableTypeId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Table type with id " + request.getTableTypeId() + " not found"
                ));
        }

        String typeName = request.getTableType();
        if (typeName != null && !typeName.trim().isEmpty()) {
            return tableTypeRepository.findByTypeNameIgnoreCase(typeName.trim())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Table type " + typeName.trim() + " not found"
                ));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table type is required");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getAllTables() {
        return tableRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getActiveTables() {
        return tableRepository.findByIsActive(true)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));
        return toResponse(table);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByType(String tableType) {
        return tableRepository.findByTableType_TypeNameIgnoreCase(tableType)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByStatus(String status) {
        try {
            RestaurantTable.TableStatus tableStatus = RestaurantTable.TableStatus.valueOf(status.toUpperCase());
            return tableRepository.findByStatus(tableStatus)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid table status: " + status
            );
        }
    }

    @Override
    public TableResponse createTable(TableRequest request) {
        // Kiểm tra xem số bàn đã tồn tại chưa
        if (tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Tạo bàn mới
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(request.getTableNumber().trim());
        table.setTableName(request.getTableName() != null ? request.getTableName().trim() : request.getTableNumber());
        table.setTableType(resolveTableType(request));
        table.setCapacity(request.getCapacity());
        table.setQrCode(request.getQrCode());
        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
        table.setIsActive(true);

        RestaurantTable saved = tableRepository.save(table);
        return toResponse(saved);
    }

    @Override
    public TableResponse updateTable(Long id, TableRequest request) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Kiểm tra xem số bàn mới có bị trùng không (ngoại trừ bàn hiện tại)
        if (!table.getTableNumber().equalsIgnoreCase(request.getTableNumber()) &&
            tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Cập nhật thông tin
        table.setTableNumber(request.getTableNumber().trim());
        table.setTableName(request.getTableName() != null ? request.getTableName().trim() : request.getTableNumber());
        table.setTableType(resolveTableType(request));
        table.setCapacity(request.getCapacity());
        if (request.getQrCode() != null) {
            table.setQrCode(request.getQrCode());
        }

        RestaurantTable updated = tableRepository.save(table);
        return toResponse(updated);
    }

    @Override
    public TableResponse updateTableStatus(Long id, UpdateTableStatusRequest request) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        if (request.getStatus() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status is required"
            );
        }

        table.setStatus(request.getStatus());
        RestaurantTable updated = tableRepository.save(table);
        return toResponse(updated);
    }

    @Override
    public TableResponse toggleTableActive(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        table.setIsActive(!table.getIsActive());
        RestaurantTable updated = tableRepository.save(table);
        return toResponse(updated);
    }

    @Override
    public void deleteTable(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        tableRepository.delete(table);
    }
}
