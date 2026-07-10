package com.swp391.api.modules.table.service.impl;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationAutoTableLockService;
import com.swp391.api.modules.reservation.service.ReservationNoShowService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lớp triển khai {@link TableService}.
 *
 * <p>Chứa các logic nghiệp vụ dùng để quản lý bàn nhà hàng:
 * - Tạo, cập nhật và xóa bàn
 * - Cập nhật trạng thái hoạt động và trạng thái sử dụng của bàn
 * - Tìm kiếm bàn theo loại, trạng thái và trạng thái hiện tại
 * </p>
 */
@Service
@Transactional
public class TableServiceImpl implements TableService {

    private static final int UPCOMING_RESERVATION_WARNING_MINUTES = 45;
    private static final int NO_SHOW_GRACE_MINUTES = 15;

    private final TableRepository tableRepository;
    private final TableTypeRepository tableTypeRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAutoTableLockService reservationAutoTableLockService;
    private final ReservationNoShowService reservationNoShowService;

    /**
     * Khởi tạo service với các repository cần dùng cho nghiệp vụ bàn.
     *
     * @param tableRepository Repository quản lý thông tin bàn
     * @param tableTypeRepository Repository quản lý loại bàn
     * @param reservationRepository Repository quản lý đặt bàn
     */
    public TableServiceImpl(TableRepository tableRepository,
                            TableTypeRepository tableTypeRepository,
                            ReservationRepository reservationRepository,
                            ReservationAutoTableLockService reservationAutoTableLockService,
                            ReservationNoShowService reservationNoShowService) {
        this.tableRepository = tableRepository;
        this.tableTypeRepository = tableTypeRepository;
        this.reservationRepository = reservationRepository;
        this.reservationAutoTableLockService = reservationAutoTableLockService;
        this.reservationNoShowService = reservationNoShowService;
    }

    /**
     * Chuyển đổi entity {@link RestaurantTable} sang DTO {@link TableResponse}
     * với trạng thái đang được lưu trong database.
     */
    private TableResponse toResponse(RestaurantTable table) {
        return toResponse(table, table.getStatus());
    }

    /**
     * Chuyển đổi entity {@link RestaurantTable} sang DTO {@link TableResponse}
     * với trạng thái truyền vào. Dùng khi cần hiển thị trạng thái động.
     */
    private TableResponse toResponse(RestaurantTable table, RestaurantTable.TableStatus status) {
        TableType tableType = table.getTableType();
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getTableName(),
            tableType != null ? tableType.getId() : null,
            tableType != null ? tableType.getTypeName() : null,
            table.getCapacity(),
            status.name(),
            table.getQrCode(),
            table.getIsActive(),
            table.getCreatedAt(),
            table.getUpdatedAt()
        );
    }

    /**
     * Xác định loại bàn từ request.
     *
     * <p>Ưu tiên tìm theo {@code tableTypeId}; nếu không có id thì tìm theo tên loại bàn.
     * Nếu thiếu loại bàn hoặc không tìm thấy, service trả về lỗi 400.</p>
     */
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
        // Lấy toàn bộ bàn, bao gồm cả bàn đang không active.
        return tableRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TableResponse> getTablesStatusNow() {
        reservationNoShowService.markNoShowsAndReleaseTables();
        reservationAutoTableLockService.lockTablesForUpcomingReservations();

        // Tính trạng thái hiển thị theo thời điểm hiện tại và các đặt bàn sắp tới.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reserveWindowStart = now.minusMinutes(NO_SHOW_GRACE_MINUTES);
        LocalDateTime reserveWindowEnd = now.plusMinutes(UPCOMING_RESERVATION_WARNING_MINUTES);
            // Nếu cộng phút bị tràn qua ngày mới thì chỉ xét đến cuối ngày hiện tại.
        List<Reservation> upcomingReservations = reservationRepository.findUpcomingConfirmedReservationsBetween(
                reserveWindowStart.toLocalDate(),
                reserveWindowStart.toLocalTime(),
                reserveWindowEnd.toLocalDate(),
                reserveWindowEnd.toLocalTime()
        );

        return tableRepository.findAll()
            .stream()
            .map(table -> toResponse(table, calculateDynamicStatus(table, upcomingReservations)))
            .collect(Collectors.toList());
    }

    /**
     * Tính trạng thái hiển thị của bàn dựa trên trạng thái lưu trong database
     * và các reservation đã xác nhận trong khung thời gian cảnh báo.
     */
    private RestaurantTable.TableStatus calculateDynamicStatus(RestaurantTable table,
                                                               List<Reservation> upcomingReservations) {
        // Các trạng thái thủ công này được ưu tiên giữ nguyên, không tự động ghi đè.
        if (table.getStatus() == RestaurantTable.TableStatus.OCCUPIED
                || table.getStatus() == RestaurantTable.TableStatus.CLEANING) {
            return table.getStatus();
        }

        boolean hasUpcomingReservation = upcomingReservations.stream()
            .anyMatch(reservation -> reservesTable(reservation, table));

        // Bàn còn trống nhưng có đặt bàn trong khung cảnh báo sẽ được hiển thị là RESERVED.
        return hasUpcomingReservation
            ? RestaurantTable.TableStatus.RESERVED
            : RestaurantTable.TableStatus.AVAILABLE;
    }

    /**
     * Kiểm tra một reservation có đang giữ bàn đang xét hay không.
     *
     * <p>Hỗ trợ cả quan hệ nhiều bàn {@code reservation.getTables()} và trường
     * {@code tableId} cũ để tương thích với dữ liệu hoặc luồng xử lý hiện có.</p>
     */
    private boolean reservesTable(Reservation reservation, RestaurantTable table) {
        if (reservation.getTables() != null
                && reservation.getTables().stream().anyMatch(currentTable -> currentTable.getId().equals(table.getId()))) {
            return true;
        }

        if (reservation.getTableId() != null) {
            return reservation.getTableId().equals(table.getId());
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getActiveTables() {
        // Chỉ trả về các bàn đang được bật sử dụng.
        return tableRepository.findByIsActive(true)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        // Nếu id không tồn tại, trả về lỗi 404 cho client.
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
        // Tìm bàn theo tên loại bàn, không phân biệt chữ hoa/thường.
        return tableRepository.findByTableType_TypeNameIgnoreCase(tableType)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByStatus(String status) {
        try {
            // Chuyển chuỗi trạng thái từ request sang enum của entity.
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
        // Kiểm tra xem số bàn đã tồn tại chưa.
        if (tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Tạo bàn mới với trạng thái mặc định là AVAILABLE và đang active.
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
        // Lấy bàn cần cập nhật, nếu không tồn tại thì trả về lỗi 404.
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Kiểm tra số bàn mới có bị trùng với bàn khác hay không.
        if (!table.getTableNumber().equalsIgnoreCase(request.getTableNumber()) &&
            tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Cập nhật thông tin cơ bản của bàn.
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
        // Chỉ cập nhật trạng thái sử dụng của bàn, không thay đổi thông tin khác.
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
        // Đảo trạng thái active để ẩn hoặc hiện bàn trong các luồng sử dụng.
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
        // Xóa bàn khỏi hệ thống sau khi kiểm tra bàn có tồn tại.
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        tableRepository.delete(table);
    }
}
