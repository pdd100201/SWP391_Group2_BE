package com.swp391.api.modules.reservation.service.impl;

import com.swp391.api.common.exception.BusinessException;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.reservation.dto.AssignTablesRequest;
import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.reservation.service.ReservationService;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.user.entity.Customer;
import com.swp391.api.modules.user.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    // Danh sách phân quyền và cấu hình thời gian mặc định của nhà hàng
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_RECEPTIONIST");
    private static final int AVERAGE_DINING_MINUTES = 90; // Thời gian ăn trung bình của khách (90 phút)
    private static final int MIN_ADVANCE_BOOKING_HOURS = 2; // Khách phải đặt trước ít nhất 2 tiếng
    private static final Set<String> RESERVATION_MANAGEMENT_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST");
    private static final Set<String> RESERVATION_VIEW_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST", "ROLE_WAITER");

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  CustomerRepository customerRepository,
                                  TableRepository tableRepository,
                                  OrderRepository orderRepository) {
        this.reservationRepository = reservationRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
    }

    // TẠO ĐƠN ĐẶT BÀN MỚI (Dành cho Khách hàng)
    @Override
    public ReservationResponse createReservation(CreateReservationRequest request) {
        // 1. Xác thực tài khoản người dùng hiện tại
        String currentEmail = getCurrentEmailRequired();
        Customer customer = customerRepository.findByCustomersEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ có khách hàng mới tạo được đơn đặt bàn!"));

        // 2. Kiểm tra quy định đặt trước 2 tiếng
        LocalDateTime reservationDateTime = LocalDateTime.of(request.getReservationDate(), request.getReservationTime());
        LocalDateTime earliestReservationDateTime = LocalDateTime.now().plusHours(MIN_ADVANCE_BOOKING_HOURS);
        if (reservationDateTime.isBefore(earliestReservationDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn phải đặt bàn trước giờ hẹn ít nhất 2 tiếng!");
        }

        // 3. Kiểm tra xem nhà hàng còn đủ tổng sức chứa (ghế trống) trong khung giờ đó hay không
        validateTableAvailability(
                request.getReservationDate(),
                request.getReservationTime(),
                request.getNumberOfGuests());

        // 4. Khởi tạo thông tin đơn đặt bàn ở trạng thái CHỜ XỬ LÝ (PENDING)
        Reservation reservation = new Reservation();
        reservation.setFullName(request.getFullName());
        reservation.setPhone(request.getPhone());
        reservation.setEmail(request.getEmail());
        reservation.setReservationDate(request.getReservationDate());
        reservation.setReservationTime(request.getReservationTime());
        reservation.setNumberOfGuests(request.getNumberOfGuests());
        reservation.setNote(request.getNote());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCustomerId(customer.getCustomerId());

        return toResponse(reservationRepository.save(reservation));
    }

    // XEM LỊCH SỬ ĐẶT BÀN CỦA TÔI (Dành cho Khách hàng)
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations() {
        String email = getCurrentEmailRequired();
        return reservationRepository.findByEmailOrderByReservationDateDescReservationTimeDescCreatedAtDesc(email).stream()
                .map(this::toResponse)
                .toList();
    }

    // XEM TẤT CẢ ĐƠN ĐẶT BÀN TRÊN HỆ THỐNG (Dành cho Nhân viên)
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        requireAnyRole(RESERVATION_VIEW_ROLES); // Yêu cầu quyền ADMIN, MANAGER, RECEPTIONIST hoặc WAITER
        return reservationRepository.findAllByOrderByReservationDateDescReservationTimeDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    // HỦY ĐƠN ĐẶT BÀN (Khách tự hủy hoặc Nhân viên hủy hộ)
    @Override
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn này"));

        // 1. Nếu đơn đã xong, đã hủy từ trước hoặc đã quá hạn (No-show) thì không cho hủy nữa
        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể hủy đơn đặt bàn ở trạng thái này!");
        }

        // 2. Nếu đơn này đã đi kèm một Order gọi món đang mở (OPEN), yêu cầu phải xử lý hủy/đóng Order trước
        orderRepository.findByReservationReservationId(reservationId)
                .filter(order -> order.getStatus() == OrderStatus.OPEN)
                .ifPresent(order -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Đơn đặt bàn này đang có hóa đơn gọi món chưa đóng. Vui lòng xử lý hóa đơn trước!"
                    );
                });

        // 3. Nếu không phải nhân viên quản lý, kiểm tra xem khách này có đúng là chủ sở hữu đơn này không
        if (!hasAnyRole(RESERVATION_MANAGEMENT_ROLES)) {
            String email = getCurrentEmailRequired();
            if (!reservation.getEmail().equalsIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền hủy đơn đặt bàn của người khác!");
            }
        }

        // 4. Cập nhật trạng thái hủy và giải phóng các bàn được gán (nếu có) sang trạng thái trống (AVAILABLE)
        reservation.setStatus(ReservationStatus.CANCELLED);
        releaseReservedTables(reservation);
        return toResponse(reservationRepository.save(reservation));
    }

    // XÁC NHẬN ĐƠN ĐẶT BÀN (Dành cho Quản lý/Lễ tân chốt lịch với khách)
    @Override
    public ReservationResponse confirmReservation(Long reservationId) {
        requireAnyRole(RESERVATION_MANAGEMENT_ROLES);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn"));

        // 1. Chỉ chấp nhận xác nhận đơn đang ở trạng thái CHỜ XỬ LÝ (PENDING)
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể xác nhận đơn đặt bàn ở trạng thái Chờ xử lý!");
        }

        // 2. Tái kiểm tra xem nhà hàng có còn đủ ghế trống hay không trước khi chốt đơn
        validateTableAvailability(
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getNumberOfGuests());

        // 3. Chuyển trạng thái sang ĐÃ XÁC NHẬN (CONFIRMED)
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservationRepository.save(reservation));
    }

    // GÁN BÀN CỤ THỂ KHI KHÁCH ĐẾN NHÀ HÀNG (Check-in đón khách)
    @Override
    public ReservationResponse assignTables(Long reservationId, AssignTablesRequest request) {
        requireAnyRole(STAFF_ROLES); // Nhân viên bất kỳ đều có thể xếp bàn cho khách

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw new BusinessException("Không thể gán bàn cho đơn đã bị hủy hoặc khách không đến!");
        }

        // 1. Kiểm tra danh sách bàn được chọn từ giao diện gửi lên
        List<RestaurantTable> selectedTables = tableRepository.findAllById(request.getTableIds());
        if (selectedTables.size() != request.getTableIds().size()) {
            throw new BusinessException("Danh sách bàn được chọn không tồn tại hoặc không hợp lệ!");
        }

        // 2. Đảm bảo toàn bộ các bàn được chọn đều đang hoạt động và đang ở trạng thái Trống hoặc Đã đặt trước
        for (RestaurantTable table : selectedTables) {
            boolean assignableStatus = table.getStatus() == RestaurantTable.TableStatus.AVAILABLE
                    || table.getStatus() == RestaurantTable.TableStatus.RESERVED;
            if (!Boolean.TRUE.equals(table.getIsActive()) || !assignableStatus) {
                throw new BusinessException("Bàn số " + table.getTableNumber() + " hiện đang không khả dụng (Đang có khách ngồi hoặc bị khóa)!");
            }
        }

        // 3. Kiểm tra xem tổng sức chứa của các bàn được chọn có đủ cho số lượng khách đi cùng không (Tránh xếp thiếu chỗ)
        int totalCapacity = selectedTables.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
        if (totalCapacity < reservation.getNumberOfGuests()) {
            throw new BusinessException("Tổng sức chứa của các bàn đã chọn (" + totalCapacity + " chỗ) không đủ cho "
                    + reservation.getNumberOfGuests() + " khách!");
        }

        // 4. Liên kết các bàn với đơn đặt, cập nhật trạng thái đơn sang ĐÃ ĐẾN (ARRIVED) và đổi trạng thái bàn sang ĐANG PHỤC VỤ (OCCUPIED)
        reservation.setTables(selectedTables);
        reservation.setTableId(selectedTables.get(0).getId()); // Ghi nhận bàn đầu tiên làm bàn đại diện chính
        reservation.setStatus(ReservationStatus.ARRIVED);

        selectedTables.forEach(table -> table.setStatus(RestaurantTable.TableStatus.OCCUPIED));
        tableRepository.saveAll(selectedTables);

        return toResponse(reservationRepository.save(reservation));
    }

    // LOGIC KIỂM TRA SỨC CHỨA CÒN TRỐNG CỦA NHÀ HÀNG (Hàm nội bộ)
    private void validateTableAvailability(LocalDate reservationDate,
                                           LocalTime requestedStart,
                                           Integer requestedGuests) {
        // Tính toán khung giờ xung đột (Lấy giờ khách đặt cộng trừ 90 phút thời gian ăn trung bình)
        LocalTime requestedEnd = requestedStart.plusMinutes(AVERAGE_DINING_MINUTES);
        LocalTime overlapStart = requestedStart.minusMinutes(AVERAGE_DINING_MINUTES);

        // 1. Tính tổng số ghế hiện có của toàn bộ nhà hàng
        long totalActiveCapacity = Optional.ofNullable(tableRepository.sumActiveRestaurantSeats()).orElse(0L);

        // 2. Tính tổng số ghế đã bị chiếm dụng bởi các đơn đặt bàn khác trong cùng khung giờ xung đột
        long unavailableCapacity = reservationRepository
                .findUnavailableTablesForReservationWindow(reservationDate, overlapStart, requestedEnd)
                .stream()
                .filter(table -> Boolean.TRUE.equals(table.getIsActive()))
                .mapToLong(RestaurantTable::getCapacity)
                .sum();

        // 3. Số ghế còn trống = Tổng số ghế - Số ghế đã bị giữ
        long availableCapacity = totalActiveCapacity - unavailableCapacity;
        if (availableCapacity < requestedGuests) {
            throw new BusinessException("Nhà hàng đã hết chỗ trong khung giờ này. Vui lòng chọn thời gian khác hoặc giảm số lượng khách!");
        }
    }

    // GIẢI PHÓNG BÀN SANG TRẠNG THÁI TRỐNG (Hàm nội bộ dùng khi hủy đơn)
    private void releaseReservedTables(Reservation reservation) {
        List<RestaurantTable> tables = reservation.getTables();
        if (tables == null || tables.isEmpty()) {
            return;
        }

        // Chuyển các bàn từ trạng thái "Đã đặt trước" (RESERVED) quay trở về "Bàn trống" (AVAILABLE)
        tables.stream()
                .filter(table -> table.getStatus() == RestaurantTable.TableStatus.RESERVED)
                .forEach(table -> table.setStatus(RestaurantTable.TableStatus.AVAILABLE));
        tableRepository.saveAll(tables);
    }

    // CHUYỂN ĐỔI THỰC THỂ ENTITY SANG ĐỐI TƯỢNG RESPONSE ĐỂ TRẢ VỀ FRONTEND (Hàm nội bộ)
    private ReservationResponse toResponse(Reservation reservation) {
        ReservationResponse response = new ReservationResponse(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                resolvePrimaryTableId(reservation),
                reservation.getFullName(),
                reservation.getPhone(),
                reservation.getEmail(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getNumberOfGuests(),
                reservation.getNote(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
        // Điền thêm thông tin Hóa đơn đi kèm (nếu đã tạo hóa đơn gọi món cho đơn đặt bàn này)
        orderRepository.findByReservationReservationId(reservation.getReservationId()).ifPresent(order -> {
            response.setOrderId(order.getId());
            response.setOrderCode(order.getOrderCode());
            response.setOrderStatus(order.getStatus());
        });
        return response;
    }

    // LẤY RA ID CỦA BÀN ĐẦU TIÊN TRONG DANH SÁCH BÀN ĐƯỢC GÁN (Hàm nội bộ)
    private Long resolvePrimaryTableId(Reservation reservation) {
        if (reservation.getTableId() != null) {
            return reservation.getTableId();
        }

        List<RestaurantTable> tables = reservation.getTables();
        if (tables == null || tables.isEmpty()) {
            return null;
        }

        return tables.get(0).getId();
    }

    // LẤY EMAIL CỦA TÀI KHOẢN ĐANG ĐĂNG NHẬP (Có thể ẩn danh/Null)
    private Optional<String> getCurrentEmailOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }

    // BẮT BUỘC PHẢI CÓ EMAIL ĐĂNG NHẬP, NẾU KHÔNG TRẢ VỀ LỖI 401 UNAUTHORIZED
    private String getCurrentEmailRequired() {
        return getCurrentEmailOptional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện thao tác này!"));
    }

    // KIỂM TRA QUYỀN HẠN, NẾU KHÔNG ĐỦ QUYỀN TRẢ VỀ LỖI 403 FORBIDDEN
    private void requireAnyRole(Set<String> roles) {
        if (!hasAnyRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện chức năng này!");
        }
    }

    // KIỂM TRA XEM TÀI KHOẢN HIỆN TẠI CÓ SỞ HỮU QUYỀN NÀO TRONG DANH SÁCH KHÔNG (Trả về True/False)
    private boolean hasAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(roles::contains);
    }
}