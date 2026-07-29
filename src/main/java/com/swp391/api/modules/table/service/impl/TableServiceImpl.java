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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Controller se goi vao cac ham trong service nay, service moi goi repository de thao tac DB.
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

    // Doi entity RestaurantTable thanh DTO tra ve cho frontend.
    private TableResponse toResponse(RestaurantTable table) {
        return toResponse(table, table.getStatus());
    }

    // Doi entity thanh DTO, nhung status co the la status duoc tinh dong.
    private TableResponse toResponse(RestaurantTable table, RestaurantTable.TableStatus status) {
        // Lay thong tin loai ban tu entity ban.
        TableType tableType = table.getTableType();

        // Tao object response gom nhung field frontend can hien thi.
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

    // Tim loai ban cho ban dang tao/sua.
    private TableType resolveTableType(TableRequest request) {
        // Neu frontend gui tableTypeId thi tim loai ban theo id.
        if (request.getTableTypeId() != null) {
            return tableTypeRepository.findById(request.getTableTypeId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Table type with id " + request.getTableTypeId() + " not found"
                ));
        }

        // Neu khong co id, lay ten loai ban tu request.
        String typeName = request.getTableType();

        // Neu co ten loai ban thi tim trong DB theo ten, khong phan biet hoa thuong.
        if (typeName != null && !typeName.trim().isEmpty()) {
            return tableTypeRepository.findByTypeNameIgnoreCase(typeName.trim())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Table type " + typeName.trim() + " not found"
                ));
        }

        // Khong co id cung khong co ten loai ban thi request khong hop le.
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table type is required");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getAllTables() {
        // Lay tat ca ban trong DB.
        return tableRepository.findAll()
            // Chuyen List thanh stream de map tung ban.
            .stream()
            // Moi RestaurantTable duoc doi thanh TableResponse.
            .map(this::toResponse)
            // Gom lai thanh List<TableResponse> de tra ve controller.
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TableResponse> getTablesStatusNow() {
        // Truoc khi hien thi floor plan, danh dau cac reservation qua gio la NO_SHOW.
        reservationNoShowService.markNoShowsAndReleaseTables();

        // Khoa/giu ban cho cac reservation sap den gio.
        reservationAutoTableLockService.lockTablesForUpcomingReservations();

        // Lay thoi diem hien tai.
        LocalDateTime now = LocalDateTime.now();

        // Lay moc bat dau: lui lai 15 phut de tinh grace time no-show.
        LocalDateTime reserveWindowStart = now.minusMinutes(NO_SHOW_GRACE_MINUTES);

        // Lay moc ket thuc: cong 45 phut de canh bao reservation sap toi.
        LocalDateTime reserveWindowEnd = now.plusMinutes(UPCOMING_RESERVATION_WARNING_MINUTES);

        // Lay cac reservation CONFIRMED nam trong khoang thoi gian vua tinh.
        List<Reservation> upcomingReservations = reservationRepository.findUpcomingConfirmedReservationsBetween(
                reserveWindowStart.toLocalDate(),
                reserveWindowStart.toLocalTime(),
                reserveWindowEnd.toLocalDate(),
                reserveWindowEnd.toLocalTime()
        );

        // Lay tat ca ban trong DB.
        return tableRepository.findAll()
            // Duyet tung ban.
            .stream()
            // Moi ban duoc tinh status hien thi roi doi sang TableResponse.
            .map(table -> toResponse(table, calculateDynamicStatus(table, upcomingReservations)))
            // Gom thanh danh sach tra ve frontend.
            .collect(Collectors.toList());
    }

    // Tinh status hien thi cua ban dua vao status DB va reservation sap toi.
    private RestaurantTable.TableStatus calculateDynamicStatus(RestaurantTable table,
                                                               List<Reservation> upcomingReservations) {
        // Lay status hien tai cua ban trong DB.
        if (table.getStatus() == RestaurantTable.TableStatus.OCCUPIED
                || table.getStatus() == RestaurantTable.TableStatus.CLEANING) {
            // Ban dang co khach/dang don dep thi giu nguyen status nay.
            return table.getStatus();
        }

        // Duyet danh sach reservation sap toi de xem co reservation nao giu ban nay khong.
        boolean hasUpcomingReservation = upcomingReservations.stream()
            .anyMatch(reservation -> reservesTable(reservation, table));

        // Neu co reservation sap toi thi hien RESERVED, neu khong thi hien AVAILABLE.
        return hasUpcomingReservation
            ? RestaurantTable.TableStatus.RESERVED
            : RestaurantTable.TableStatus.AVAILABLE;
    }

    // Kiem tra reservation co dang giu ban nay khong.
    private boolean reservesTable(Reservation reservation, RestaurantTable table) {
        // Kiem tra kieu moi: reservation co danh sach nhieu ban.
        if (reservation.getTables() != null
                && reservation.getTables().stream().anyMatch(currentTable -> currentTable.getId().equals(table.getId()))) {
            // Neu id ban dang xet nam trong danh sach ban cua reservation thi dung.
            return true;
        }

        // Kiem tra kieu cu: reservation chi co mot tableId.
        if (reservation.getTableId() != null) {
            // So sanh tableId cua reservation voi id ban dang xet.
            return reservation.getTableId().equals(table.getId());
        }

        // Khong khop ca hai kieu thi reservation khong giu ban nay.
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getActiveTables() {
        // Lay cac ban co isActive = true.
        return tableRepository.findByIsActive(true)
            // Duyet tung ban active.
            .stream()
            // Doi entity thanh response.
            .map(this::toResponse)
            // Gom thanh list tra ve.
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        // Tim ban theo id frontend/controller truyen vao.
        RestaurantTable table = tableRepository.findById(id)
            // Neu khong tim thay thi nem loi 404.
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Tim thay thi doi sang response tra ve.
        return toResponse(table);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByType(String tableType) {
        // Goi repository tim ban theo ten loai ban.
        return tableRepository.findByTableType_TypeNameIgnoreCase(tableType)
            // Duyet ket qua tim duoc.
            .stream()
            // Doi tung ban thanh response.
            .map(this::toResponse)
            // Gom thanh list tra ve.
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByStatus(String status) {
        try {
            // Doi chuoi status frontend gui len sang enum TableStatus.
            RestaurantTable.TableStatus tableStatus = RestaurantTable.TableStatus.valueOf(status.toUpperCase());

            // Tim cac ban co status vua parse duoc.
            return tableRepository.findByStatus(tableStatus)
                // Duyet danh sach ban.
                .stream()
                // Doi entity thanh response.
                .map(this::toResponse)
                // Gom thanh list tra ve.
                .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            // Neu status khong hop le, vi du "BUSY", thi tra loi 400.
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid table status: " + status
            );
        }
    }

    @Override
    public TableResponse createTable(TableRequest request) {
        // Kiem tra trong DB da co tableNumber nay chua.
        if (tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            // Neu trung tableNumber thi tra loi 409 Conflict.
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Tao entity ban moi.
        RestaurantTable table = new RestaurantTable();

        // Luu so ban, trim de bo khoang trang dau/cuoi.
        table.setTableNumber(request.getTableNumber().trim());

        // Neu co ten ban thi dung ten do, neu khong thi lay tableNumber lam ten ban.
        table.setTableName(request.getTableName() != null ? request.getTableName().trim() : request.getTableNumber());

        // Tim loai ban hop le roi gan vao ban.
        table.setTableType(resolveTableType(request));

        // Gan suc chua cua ban.
        table.setCapacity(request.getCapacity());

        // Gan QR code neu frontend co gui.
        table.setQrCode(request.getQrCode());

        // Ban moi mac dinh la ban trong.
        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);

        // Ban moi mac dinh duoc bat su dung.
        table.setIsActive(true);

        // Luu ban moi vao DB.
        RestaurantTable saved = tableRepository.save(table);

        // Doi ban vua luu thanh response tra ve frontend.
        return toResponse(saved);
    }

    @Override
    public TableResponse updateTable(Long id, TableRequest request) {
        // Tim ban can sua theo id.
        RestaurantTable table = tableRepository.findById(id)
            // Neu id khong ton tai thi tra 404.
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Neu tableNumber moi khac tableNumber cu va da ton tai trong DB thi bao trung.
        if (!table.getTableNumber().equalsIgnoreCase(request.getTableNumber()) &&
            tableRepository.existsByTableNumberIgnoreCase(request.getTableNumber())) {
            // Tra ve 409 vi khong cho hai ban cung mot so ban.
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Table number " + request.getTableNumber() + " already exists"
            );
        }

        // Cap nhat so ban.
        table.setTableNumber(request.getTableNumber().trim());

        // Cap nhat ten ban, neu bo trong thi fallback ve tableNumber.
        table.setTableName(request.getTableName() != null ? request.getTableName().trim() : request.getTableNumber());

        // Cap nhat loai ban.
        table.setTableType(resolveTableType(request));

        // Cap nhat suc chua.
        table.setCapacity(request.getCapacity());

        // Chi cap nhat QR code neu request co gui gia tri.
        if (request.getQrCode() != null) {
            table.setQrCode(request.getQrCode());
        }

        // Luu entity da sua vao DB.
        RestaurantTable updated = tableRepository.save(table);

        // Tra ve ban sau khi update.
        return toResponse(updated);
    }

    @Override
    public TableResponse updateTableStatus(Long id, UpdateTableStatusRequest request) {
        // Tim ban can doi status theo id.
        RestaurantTable table = tableRepository.findById(id)
            // Khong tim thay thi tra 404.
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Kiem tra frontend co gui status moi khong.
        if (request.getStatus() == null) {
            // Khong co status thi request sai, tra 400.
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status is required"
            );
        }

        // Gan status moi cho ban.
        table.setStatus(request.getStatus());

        // Luu status moi vao DB.
        RestaurantTable updated = tableRepository.save(table);

        // Tra ban sau khi doi status ve frontend.
        return toResponse(updated);
    }

    @Override
    public TableResponse toggleTableActive(Long id) {
        // Tim ban can bat/tat active.
        RestaurantTable table = tableRepository.findById(id)
            // Khong tim thay thi tra 404.
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Dao nguoc isActive: true -> false, false -> true.
        table.setIsActive(!table.getIsActive());

        // Luu trang thai active moi vao DB.
        RestaurantTable updated = tableRepository.save(table);

        // Tra ve ban sau khi toggle.
        return toResponse(updated);
    }

    @Override
    public void deleteTable(Long id) {
        // Tim ban can xoa.
        RestaurantTable table = tableRepository.findById(id)
            // Khong tim thay thi tra 404.
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table with id " + id + " not found"
            ));

        // Xoa that ban khoi database.
        tableRepository.delete(table);
    }
}
