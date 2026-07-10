package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationAutoTableLockService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationAutoTableLockServiceImpl implements ReservationAutoTableLockService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAutoTableLockServiceImpl.class);
    // Tự động khóa bàn cho các đơn đặt bàn sẽ đến trong vòng 45 phút.
    private static final int AUTO_LOCK_BEFORE_MINUTES = 45;
    // Vẫn xét các đơn đã quá giờ tối đa 15 phút để tránh bỏ sót trước khi chuyển NO_SHOW.
    private static final int AUTO_LOCK_AFTER_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;

    /**
     * Khởi tạo service với repository quản lý reservation và bàn.
     */
    public ReservationAutoTableLockServiceImpl(ReservationRepository reservationRepository,
                                               TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
    }

    /**
     * Tự động chọn và khóa bàn cho các đơn đặt bàn đã được xác nhận.
     *
     * <p>Hàm này thường được scheduler gọi định kỳ. Hệ thống tìm các đơn CONFIRMED
     * trong khoảng từ 15 phút trước đến 45 phút sau thời điểm hiện tại. Nếu đơn chưa
     * được gán bàn, hệ thống sẽ chọn tổ hợp bàn phù hợp nhất và đổi trạng thái bàn
     * sang RESERVED.</p>
     */
    @Override
    @Transactional
    public void lockTablesForUpcomingReservations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromDateTime = now.minusMinutes(AUTO_LOCK_AFTER_MINUTES);
        LocalDateTime toDateTime = now.plusMinutes(AUTO_LOCK_BEFORE_MINUTES);
        if (toDateTime.isBefore(now)) {
            // Nếu cộng phút bị tràn qua ngày mới thì chỉ xét đến cuối ngày hiện tại.
            toDateTime = toDateTime;
        }

        // Lấy các đơn đã xác nhận trong khung thời gian cần tự động giữ bàn.
        List<Reservation> reservations = reservationRepository.findUpcomingConfirmedReservationsBetween(
                fromDateTime.toLocalDate(),
                fromDateTime.toLocalTime(),
                toDateTime.toLocalDate(),
                toDateTime.toLocalTime()
        );

        // Chỉ dùng các bàn đang active và AVAILABLE, sắp xếp tăng dần theo sức chứa.
        List<RestaurantTable> availableTables = tableRepository.findAvailableActiveTablesOrderByCapacityAsc();
        List<RestaurantTable> changedTables = new ArrayList<>();
        boolean changed = false;

        for (Reservation reservation : reservations) {
            // Nếu đơn đã có bàn thì không gán lại để tránh ghi đè lựa chọn của nhân viên.
            if (reserveExistingTables(reservation, availableTables, changedTables)) {
                changed = true;
                continue;
            }

            // Tìm tổ hợp bàn đủ chỗ cho số lượng khách của reservation.
            List<RestaurantTable> selectedTables = findBestTableCombination(
                    availableTables,
                    reservation.getNumberOfGuests()
            );

            if (selectedTables.isEmpty()) {
                // Không tìm được bàn phù hợp thì ghi log để nhân viên/quản trị kiểm tra.
                log.warn("No available table combination can fit reservation {} for {} guests",
                        reservation.getReservationId(),
                        reservation.getNumberOfGuests());
                continue;
            }

            // Liên kết bàn với reservation và chuyển trạng thái bàn sang RESERVED.
            reservation.setTables(selectedTables);
            reservation.setTableId(selectedTables.get(0).getId());
            selectedTables.forEach(table -> table.setStatus(RestaurantTable.TableStatus.RESERVED));
            availableTables.removeAll(selectedTables);
            changedTables.addAll(selectedTables);
            changed = true;
        }

        if (changed) {
            // Lưu lại reservation và trạng thái các bàn đã bị thay đổi.
            reservationRepository.saveAll(reservations);
            tableRepository.saveAll(changedTables);
        }
    }

    /**
     * Kiểm tra reservation đã có bàn đại diện hoặc danh sách bàn được gán hay chưa.
     */
    private boolean reserveExistingTables(Reservation reservation,
                                          List<RestaurantTable> availableTables,
                                          List<RestaurantTable> changedTables) {
        List<RestaurantTable> assignedTables = new ArrayList<>();
        if (reservation.getTables() != null && !reservation.getTables().isEmpty()) {
            assignedTables.addAll(reservation.getTables());
        } else if (reservation.getTableId() != null) {
            tableRepository.findById(reservation.getTableId()).ifPresent(assignedTables::add);
        }

        if (assignedTables.isEmpty()) {
            return false;
        }

        List<RestaurantTable> reservableTables = new ArrayList<>(assignedTables.stream()
                .filter(table -> Boolean.TRUE.equals(table.getIsActive()))
                .filter(table -> table.getStatus() == RestaurantTable.TableStatus.AVAILABLE
                        || table.getStatus() == RestaurantTable.TableStatus.RESERVED)
                .toList());

        if (reservableTables.isEmpty()) {
            log.warn("Reservation {} already has assigned tables, but none can be reserved",
                    reservation.getReservationId());
            return true;
        }

        reservation.setTables(reservableTables);
        reservation.setTableId(reservableTables.get(0).getId());
        reservableTables.forEach(table -> table.setStatus(RestaurantTable.TableStatus.RESERVED));
        availableTables.removeAll(reservableTables);
        changedTables.addAll(reservableTables);
        return true;
    }

    /**
     * Tìm tổ hợp bàn tốt nhất cho số lượng khách yêu cầu.
     *
     * <p>Nếu dữ liệu đầu vào không hợp lệ hoặc không còn bàn trống thì trả về danh sách rỗng.</p>
     */
    private List<RestaurantTable> findBestTableCombination(List<RestaurantTable> availableTables,
                                                           Integer requestedGuests) {
        if (requestedGuests == null || requestedGuests <= 0 || availableTables.isEmpty()) {
            return List.of();
        }

        List<RestaurantTable> best = new ArrayList<>();
        findBestTableCombination(availableTables, requestedGuests, 0, new ArrayList<>(), best);
        return best;
    }

    /**
     * Duyệt đệ quy tất cả tổ hợp bàn có thể chọn.
     *
     * <p>Mỗi khi tổ hợp hiện tại đủ sức chứa, so sánh với tổ hợp tốt nhất đang có
     * để giữ lại phương án tối ưu hơn.</p>
     */
    private void findBestTableCombination(List<RestaurantTable> availableTables,
                                          int requestedGuests,
                                          int startIndex,
                                          List<RestaurantTable> current,
                                          List<RestaurantTable> best) {
        int currentCapacity = totalCapacity(current);
        if (currentCapacity >= requestedGuests) {
            if (isBetterCombination(current, best, requestedGuests)) {
                best.clear();
                best.addAll(current);
            }
            return;
        }

        // Thử thêm từng bàn tiếp theo vào tổ hợp hiện tại rồi quay lui để thử phương án khác.
        for (int index = startIndex; index < availableTables.size(); index++) {
            current.add(availableTables.get(index));
            findBestTableCombination(availableTables, requestedGuests, index + 1, current, best);
            current.remove(current.size() - 1);
        }
    }

    /**
     * So sánh hai tổ hợp bàn để chọn phương án tốt hơn.
     *
     * <p>Ưu tiên tổng sức chứa nhỏ hơn để tránh lãng phí ghế. Nếu bằng nhau thì ưu tiên
     * dùng ít bàn hơn. Nếu vẫn bằng nhau thì chọn tổ hợp có id bàn nhỏ hơn để kết quả ổn định.</p>
     */
    private boolean isBetterCombination(List<RestaurantTable> current,
                                        List<RestaurantTable> best,
                                        int requestedGuests) {
        if (best.isEmpty()) {
            return true;
        }

        int currentCapacity = totalCapacity(current);
        int bestCapacity = totalCapacity(best);
        if (currentCapacity != bestCapacity) {
            return currentCapacity < bestCapacity;
        }

        if (current.size() != best.size()) {
            return current.size() < best.size();
        }

        Long currentFirstId = current.stream().map(RestaurantTable::getId).min(Comparator.naturalOrder()).orElse(Long.MAX_VALUE);
        Long bestFirstId = best.stream().map(RestaurantTable::getId).min(Comparator.naturalOrder()).orElse(Long.MAX_VALUE);
        return currentFirstId < bestFirstId;
    }

    /**
     * Tính tổng sức chứa của một danh sách bàn.
     */
    private int totalCapacity(List<RestaurantTable> tables) {
        return tables.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
    }
}
