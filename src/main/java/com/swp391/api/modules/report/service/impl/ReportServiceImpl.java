package com.swp391.api.modules.report.service.impl;

import com.swp391.api.modules.report.dto.GroupByMode;
import com.swp391.api.modules.report.dto.RevenueStatsResponse;
import com.swp391.api.modules.report.dto.RevenueTimeStatsDto;
import com.swp391.api.modules.report.service.ReportService;
import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import com.swp391.api.modules.payment.repository.PaymentRepository;
import java.math.BigDecimal;
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

    public ReportServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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

        // 5. Gom nhóm các khoản thanh toán theo mốc thời gian (Ngày/Tháng/Năm)
        Map<String, List<Payment>> groupedMap = groupPaymentsByTime(payments, mode);

        // 6. Lấp đầy các mốc thời gian không có giao dịch với doanh thu bằng 0
        List<RevenueTimeStatsDto> chartData = fillZeroRevenueDays(startDate, endDate, mode, groupedMap);

        // 7. Tạo phản hồi dữ liệu tối giản
        return new RevenueStatsResponse(totalRevenuePeriod, transactionCountPeriod, chartData);
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
        if (mode == GroupByMode.DAY) {
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
    private Map<String, List<Payment>> groupPaymentsByTime(List<Payment> payments, GroupByMode mode) {
        Map<String, List<Payment>> map = new HashMap<>();
        for (Payment payment : payments) {
            LocalDateTime paidTime = payment.getPaidAt();
            if (paidTime == null) {
                continue;
            }

            String label = "";
            if (mode == GroupByMode.DAY) {
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

        if (mode == GroupByMode.DAY) {
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
}
