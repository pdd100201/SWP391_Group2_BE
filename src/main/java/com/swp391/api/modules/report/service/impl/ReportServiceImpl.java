package com.swp391.api.modules.report.service.impl;

import com.swp391.api.modules.report.dto.DashboardStatsResponse;
import com.swp391.api.modules.report.dto.GroupByMode;
import com.swp391.api.modules.report.dto.RevenueStatsResponse;
import com.swp391.api.modules.report.dto.RevenueTimeStatsDto;
import com.swp391.api.modules.report.dto.PaymentTransactionDto;
import com.swp391.api.modules.report.dto.OrderItemDto;
import com.swp391.api.modules.report.dto.TopSellingItemDto;
import com.swp391.api.modules.report.dto.PaymentMethodBreakdownDto;
import com.swp391.api.modules.order.entity.OrderItem;
import com.swp391.api.modules.order.entity.OrderItemStatus;
import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.order.entity.RestaurantOrder;
import com.swp391.api.modules.order.repository.OrderRepository;
import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.report.service.ReportService;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.reservation.repository.ReservationRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lớp hiện thực dịch vụ thống kê doanh thu đơn giản cho Báo cáo doanh thu (Reports).
 * Lớp này được chia nhỏ thành các hàm đơn nhiệm, dễ học và dễ đọc.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository paymentRepository;
    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public ReportServiceImpl(
            PaymentRepository paymentRepository,
            TableRepository tableRepository,
            ReservationRepository reservationRepository,
            MenuItemRepository menuItemRepository,
            UserRepository userRepository,
            OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Hàm điều phối chính để tính toán doanh thu dòng tiền.
     * Đầu vào: startDate (ngày bắt đầu), endDate (ngày kết thúc), mode (chế độ nhóm: DAY, MONTH, YEAR).
     * Kết quả trả về: Đối tượng RevenueStatsResponse chứa tổng số liệu và danh sách vẽ biểu đồ.
     */
    @Override
    public RevenueStatsResponse getRevenueStatistics(LocalDate startDate, LocalDate endDate, GroupByMode mode) {
        // 1. Kiểm tra tính hợp lệ của khoảng ngày truyền vào
        validateDates(startDate, endDate, mode);

        // 2. Chuyển đổi ranh giới ngày sang LocalDateTime (start: đầu ngày bắt đầu, endExclusive: đầu ngày hôm sau ngày kết thúc)
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        // 3. Gọi repository lấy các khoản thanh toán thành công (PAID) trong khoảng thời gian [start, endExclusive)
        List<Payment> payments = paymentRepository.findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            PaymentStatus.PAID, start, endExclusive
        );

        // 4. Tính toán tổng số liệu doanh thu trong kỳ lọc
        BigDecimal totalRevenuePeriod = calculateTotalRevenue(payments);
        long transactionCountPeriod = payments.size();

        // 5. Gom nhóm các khoản thanh toán theo mốc thời gian (Giờ/Ngày/Tháng/Năm)
        Map<String, List<Payment>> groupedMap = groupPaymentsByTime(payments, mode, startDate, endDate);

        // 6. Lấp đầy các mốc thời gian không có giao dịch với doanh thu bằng 0
        List<RevenueTimeStatsDto> chartData = fillZeroRevenueDays(startDate, endDate, mode, groupedMap);

        // 7. Chi tiết các giao dịch trong kỳ
        List<PaymentTransactionDto> transactions = mapToTransactionDtos(payments);

        // 8. Thống kê món bán chạy nhất
        List<TopSellingItemDto> topSellingItems = calculateTopSellingItems(payments);

        // 9. Thống kê phương thức thanh toán
        List<PaymentMethodBreakdownDto> paymentMethods = calculatePaymentMethodsBreakdown(payments);

        // 10. Tính toán AOV
        BigDecimal averageOrderValue = calculateAverageOrderValue(totalRevenuePeriod, transactionCountPeriod);

        // 11. Tạo phản hồi dữ liệu chi tiết
        return new RevenueStatsResponse(
            totalRevenuePeriod, 
            transactionCountPeriod, 
            chartData, 
            transactions, 
            topSellingItems, 
            paymentMethods, 
            averageOrderValue
        );
    }

    /**
     * Lấy dữ liệu thống kê hoạt động tổng hợp cho trang chủ Dashboard.
     * Đầu vào: Không có.
     * Kết quả: DashboardStatsResponse chứa tổng số lượng bàn, thực đơn, nhân viên, doanh thu 30 ngày qua.
     */
    @Override
    public DashboardStatsResponse getDashboardOverview() {
        // Lấy thống kê doanh thu 30 ngày qua làm mốc tổng quan
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        List<Payment> payments = paymentRepository.findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            PaymentStatus.PAID, start, endExclusive
        );

        BigDecimal totalRevenue = calculateTotalRevenue(payments);
        long successfulTransactions = payments.size();

        long totalTables = tableRepository.count();
        long totalReservations = reservationRepository.count();
        long totalMenuItems = menuItemRepository.count();
        long totalStaff = userRepository.count();

        return new DashboardStatsResponse(
            totalRevenue,
            successfulTransactions,
            totalTables,
            totalReservations,
            totalMenuItems,
            totalStaff
        );
    }

    /**
     * Hàm kiểm tra tính hợp lệ của khoảng thời gian lọc và giới hạn dung lượng truy vấn.
     * Đầu vào: startDate, endDate, mode.
     * Kết quả: Ném lỗi IllegalArgumentException nếu không hợp lệ.
     */
    private void validateDates(LocalDate startDate, LocalDate endDate, GroupByMode mode) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        // Giới hạn khoảng lọc tối đa để bảo vệ hiệu năng ứng dụng
        if (mode == GroupByMode.HOUR) {
            long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 7) {
                throw new IllegalArgumentException("Hourly view allows a maximum filter range of 7 days.");
            }
        } else if (mode == GroupByMode.DAY) {
            long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 366) {
                throw new IllegalArgumentException("Daily view allows a maximum filter range of 366 days.");
            }
        } else if (mode == GroupByMode.MONTH) {
            long monthsBetween = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(endDate));
            if (monthsBetween > 60) {
                throw new IllegalArgumentException("Monthly view allows a maximum filter range of 60 months.");
            }
        } else if (mode == GroupByMode.YEAR) {
            long yearsBetween = endDate.getYear() - startDate.getYear();
            if (yearsBetween > 30) {
                throw new IllegalArgumentException("Yearly view allows a maximum filter range of 30 years.");
            }
        }
    }

    /**
     * Hàm tính tổng số tiền của danh sách các khoản thanh toán.
     * Đầu vào: Danh sách payments.
     * Kết quả trả về: Tổng tiền dưới dạng BigDecimal.
     */
    private BigDecimal calculateTotalRevenue(List<Payment> payments) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment.getAmount() != null) {
                sum = sum.add(payment.getAmount());
            }
        }
        return sum;
    }

    /**
     * Hàm gom nhóm danh sách thanh toán thô vào Map theo định dạng nhãn thời gian tương ứng.
     * Đầu vào: Danh sách payments, chế độ nhóm mode.
     * Kết quả trả về: Map với khóa là chuỗi nhãn thời gian (ví dụ: "2026-07-17") và giá trị là danh sách payments của mốc đó.
     */
    private Map<String, List<Payment>> groupPaymentsByTime(List<Payment> payments, GroupByMode mode, LocalDate startDate, LocalDate endDate) {
        Map<String, List<Payment>> map = new HashMap<>();
        for (Payment payment : payments) {
            LocalDateTime paidTime = payment.getPaidAt();
            if (paidTime == null) {
                continue;
            }

            String label = "";
            if (mode == GroupByMode.HOUR) {
                if (startDate.equals(endDate)) {
                    label = String.format("%02d:00", paidTime.getHour()); // Định dạng: "HH:00"
                } else {
                    label = String.format("%s %02d:00", paidTime.toLocalDate().toString(), paidTime.getHour()); // Định dạng: "YYYY-MM-DD HH:00"
                }
            } else if (mode == GroupByMode.DAY) {
                label = paidTime.toLocalDate().toString(); // Định dạng: "YYYY-MM-DD"
            } else if (mode == GroupByMode.MONTH) {
                label = YearMonth.from(paidTime).toString(); // Định dạng: "YYYY-MM"
            } else if (mode == GroupByMode.YEAR) {
                label = String.valueOf(paidTime.getYear()); // Định dạng: "YYYY"
            }

            if (!label.isEmpty()) {
                map.computeIfAbsent(label, k -> new ArrayList<>()).add(payment);
            }
        }
        return map;
    }

    /**
     * Hàm lặp từ ngày bắt đầu đến ngày kết thúc để tạo ra chuỗi mốc thời gian liên tục.
     * Lấp đầy các mốc không có giao dịch bằng doanh thu 0 để tránh đứt đoạn biểu đồ.
     * Đầu vào: startDate, endDate, mode, groupedMap (Map đã gom nhóm thực tế).
     * Kết quả trả về: Danh sách RevenueTimeStatsDto đã được điền đầy đủ và sắp xếp tăng dần.
     */
    private List<RevenueTimeStatsDto> fillZeroRevenueDays(
            LocalDate startDate, LocalDate endDate, GroupByMode mode, Map<String, List<Payment>> groupedMap) {
        
        List<RevenueTimeStatsDto> chartData = new ArrayList<>();

        if (mode == GroupByMode.HOUR) {
            if (startDate.equals(endDate)) {
                for (int h = 0; h < 24; h++) {
                    String label = String.format("%02d:00", h);
                    chartData.add(buildTimeStatsDto(label, groupedMap.get(label)));
                }
            } else {
                LocalDateTime current = startDate.atStartOfDay();
                LocalDateTime end = endDate.plusDays(1).atStartOfDay();
                while (current.isBefore(end)) {
                    String label = String.format("%s %02d:00", current.toLocalDate().toString(), current.getHour());
                    chartData.add(buildTimeStatsDto(label, groupedMap.get(label)));
                    current = current.plusHours(1);
                }
            }
        } else if (mode == GroupByMode.DAY) {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String label = current.toString();
                chartData.add(buildTimeStatsDto(label, groupedMap.get(label)));
                current = current.plusDays(1);
            }
        } else if (mode == GroupByMode.MONTH) {
            YearMonth current = YearMonth.from(startDate);
            YearMonth targetEnd = YearMonth.from(endDate);
            while (!current.isAfter(targetEnd)) {
                String label = current.toString();
                chartData.add(buildTimeStatsDto(label, groupedMap.get(label)));
                current = current.plusMonths(1);
            }
        } else if (mode == GroupByMode.YEAR) {
            int currentYear = startDate.getYear();
            int endYear = endDate.getYear();
            while (currentYear <= endYear) {
                String label = String.valueOf(currentYear);
                chartData.add(buildTimeStatsDto(label, groupedMap.get(label)));
                currentYear++;
            }
        }

        return chartData;
    }

    /**
     * Hàm phụ trợ dựng đối tượng DTO doanh thu cho một mốc thời gian từ danh sách payments thô.
     * Đầu vào: nhãn thời gian label, danh sách payments của mốc thời gian đó (có thể null).
     * Kết quả trả về: RevenueTimeStatsDto chứa nhãn thời gian, tổng tiền và số giao dịch của mốc đó.
     */
    private RevenueTimeStatsDto buildTimeStatsDto(String label, List<Payment> periodPayments) {
        if (periodPayments == null || periodPayments.isEmpty()) {
            return new RevenueTimeStatsDto(label, BigDecimal.ZERO, 0L);
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (Payment payment : periodPayments) {
            if (payment.getAmount() != null) {
                sum = sum.add(payment.getAmount());
            }
        }
        return new RevenueTimeStatsDto(label, sum, (long) periodPayments.size());
    }

    private List<PaymentTransactionDto> mapToTransactionDtos(List<Payment> payments) {
        List<PaymentTransactionDto> dtos = new ArrayList<>();
        for (Payment payment : payments) {
            PaymentTransactionDto dto = new PaymentTransactionDto();
            dto.setId(payment.getId());
            dto.setPaymentCode(payment.getPaymentCode());
            dto.setAmount(payment.getAmount());
            dto.setProvider(payment.getProvider());
            dto.setPaidAt(payment.getPaidAt());
            if (payment.getBill() != null) {
                dto.setSubtotal(payment.getBill().getSubtotal());
                dto.setDiscountAmount(payment.getBill().getDiscountAmount());
                dto.setTotal(payment.getBill().getTotal());
                if (payment.getBill().getPromotion() != null) {
                    dto.setPromotionId(payment.getBill().getPromotion().getId());
                    dto.setPromotionCode(payment.getBill().getPromotion().getCode());
                    dto.setPromotionName(payment.getBill().getPromotion().getName());
                }
            }

            if (payment.getOrder() != null) {
                dto.setOrderId(payment.getOrder().getId());
                dto.setOrderCode(payment.getOrder().getOrderCode());
                if (payment.getOrder().getWaiter() != null) {
                    dto.setWaiterName(payment.getOrder().getWaiter().getFullName());
                }
                if (payment.getOrder().getReservation() != null) {
                    Reservation reservation = payment.getOrder().getReservation();
                    dto.setGuestName(reservation.getFullName());
                    if (reservation.getTables() != null && !reservation.getTables().isEmpty()) {
                        List<String> tableList = new ArrayList<>();
                        for (RestaurantTable t : reservation.getTables()) {
                            tableList.add(t.getTableName() != null && !t.getTableName().isEmpty() ? t.getTableName() : t.getTableNumber());
                        }
                        dto.setTableNames(String.join(", ", tableList));
                    }
                }
            }
            dto.setItems(mapPaymentOrderItems(payment));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Thống kê Top 10 món ăn bán chạy nhất dựa trên tổng số lượng gọi và tổng doanh thu mang về.
     * 
     * @param payments Danh sách các hóa đơn đã thanh toán thành công
     * @return Danh sách tối đa 10 món bán chạy nhất sắp xếp giảm dần theo số lượng
     */
    private List<TopSellingItemDto> calculateTopSellingItems(List<Payment> payments) {
        Map<String, TopSellingItemDto> map = new HashMap<>();
        for (Payment payment : payments) {
            for (RestaurantOrder order : findOrdersForPayment(payment)) {
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        if (!isBillableItem(item)) {
                            continue;
                        }
                        String name = item.getMenuItemName();
                        TopSellingItemDto dto = map.computeIfAbsent(name, k -> new TopSellingItemDto(name, 0L, BigDecimal.ZERO));
                        dto.setQuantity(dto.getQuantity() + item.getQuantity());
                        BigDecimal itemRevenue = item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO;
                        dto.setRevenue(dto.getRevenue().add(itemRevenue));
                    }
                }
            }
        }
        List<TopSellingItemDto> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Long.compare(b.getQuantity(), a.getQuantity()));
        return list.size() > 10 ? list.subList(0, 10) : list;
    }

    /**
     * Chuyển đổi danh sách món ăn thuộc các Order của hóa đơn sang danh sách OrderItemDto.
     * 
     * @param payment Hóa đơn cần truy xuất danh sách món
     * @return Danh sách món ăn DTO phục vụ hiển thị chi tiết hóa đơn
     */
    private List<OrderItemDto> mapPaymentOrderItems(Payment payment) {
        List<OrderItemDto> itemDtos = new ArrayList<>();
        for (RestaurantOrder order : findOrdersForPayment(payment)) {
            if (order.getItems() == null) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                if (!isBillableItem(item)) {
                    continue;
                }
                itemDtos.add(new OrderItemDto(
                    item.getId(),
                    item.getMenuItemName(),
                    item.getUnitPrice(),
                    item.getSubtotal(),
                    item.getQuantity(),
                    item.getNote(),
                    item.getStatus() != null ? item.getStatus().name() : null,
                    item.getVoidReason()
                ));
            }
        }
        return itemDtos;
    }

    /**
     * Tìm danh sách các đơn gọi món (Order) liên kết với hóa đơn thanh toán.
     * 
     * @param payment Hóa đơn cần truy vấn
     * @return Danh sách các Order hợp lệ (loại bỏ các Order đã CANCELLED)
     */
    private List<RestaurantOrder> findOrdersForPayment(Payment payment) {
        if (payment.getBill() != null
                && payment.getBill().getReservation() != null
                && payment.getBill().getReservation().getReservationId() != null) {
            return orderRepository.findAllByReservationReservationIdOrderByCreatedAtDesc(
                            payment.getBill().getReservation().getReservationId())
                    .stream()
                    .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                    .toList();
        }
        if (payment.getOrder() != null) {
            return List.of(payment.getOrder());
        }
        return List.of();
    }

    /**
     * Thống kê cơ cấu các phương thức thanh toán (Tiền mặt / Chuyển khoản ngân hàng / VNPay / etc.).
     * 
     * @param payments Danh sách các khoản thanh toán
     * @return Danh sách cơ cấu phương thức thanh toán kèm tổng tiền và số giao dịch
     */
    private List<PaymentMethodBreakdownDto> calculatePaymentMethodsBreakdown(List<Payment> payments) {
        Map<String, PaymentMethodBreakdownDto> map = new HashMap<>();
        for (Payment payment : payments) {
            String provider = payment.getProvider() != null ? payment.getProvider().toUpperCase() : "UNKNOWN";
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            PaymentMethodBreakdownDto dto = map.computeIfAbsent(provider, k -> new PaymentMethodBreakdownDto(provider, 0L, BigDecimal.ZERO));
            dto.setCount(dto.getCount() + 1);
            dto.setTotalAmount(dto.getTotalAmount().add(amount));
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Tính toán Giá trị trung bình trên mỗi đơn hàng (Average Order Value - AOV).
     * Công thức: AOV = Tổng doanh thu / Tổng số giao dịch.
     * 
     * @param totalRevenue Tổng doanh thu trong kỳ
     * @param transactionCount Tổng số giao dịch trong kỳ
     * @return Giá trị trung bình của một đơn hàng (AOV)
     */
    private BigDecimal calculateAverageOrderValue(BigDecimal totalRevenue, long transactionCount) {
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalRevenue.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * Kiểm tra xem 1 món ăn trong Order có được tính tiền vào hóa đơn hay không.
     * Loại bỏ các món có trạng thái CANCELLED (Hủy món) hoặc VOIDED (Hủy sau khi ra món).
     * 
     * @param item Món ăn cần kiểm tra
     * @return true nếu món ăn hợp lệ được tính tiền, false nếu món ăn bị hủy
     */
    private boolean isBillableItem(OrderItem item) {
        return item.getStatus() != OrderItemStatus.CANCELLED && item.getStatus() != OrderItemStatus.VOIDED;
    }
}
