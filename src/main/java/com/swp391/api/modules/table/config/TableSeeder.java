package com.swp391.api.modules.table.config;

import com.swp391.api.modules.table.entity.RestaurantTable;
import com.swp391.api.modules.table.entity.TableType;
import com.swp391.api.modules.table.repository.TableRepository;
import com.swp391.api.modules.table.repository.TableTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component khởi tạo dữ liệu mẫu cho bảng {@code restaurant_tables} khi ứng dụng khởi động.
 *
 * <p>Chạy tự động khi ứng dụng start vì implement {@link CommandLineRunner}.
 * Chỉ thêm dữ liệu khi bảng trống (count == 0) để tránh trùng lặp khi restart.</p>
 *
 * <p>{@code @Order(3)} đảm bảo seeder này chạy sau {@code DefaultUsersSeeder} (Order 1)
 * sau các seeder nền tảng để giữ thứ tự khởi tạo nhất quán.</p>
 *
 * Seeds sample restaurant tables on startup if the table is empty.
 */
@Component
@Order(3)
public class TableSeeder implements CommandLineRunner {

    /** Repository để lưu dữ liệu seed vào DB */
    private final TableRepository tableRepository;
    private final TableTypeRepository tableTypeRepository;

    /**
     * Constructor injection TableRepository.
     *
     * @param tableRepository Repository để thao tác với bảng restaurant_tables
     */
    public TableSeeder(TableRepository tableRepository, TableTypeRepository tableTypeRepository) {
        this.tableRepository = tableRepository;
        this.tableTypeRepository = tableTypeRepository;
    }

    /**
     * Phương thức thực thi khi ứng dụng khởi động.
     * Kiểm tra nếu bảng đã có dữ liệu thì bỏ qua, không seed lại.
     * Tạo danh sách 10 bàn mẫu đại diện cho các khu vực khác nhau của nhà hàng.
     *
     * @param args Tham số command line (không dùng trong trường hợp này)
     */
    @Override
    public void run(String... args) {
        // Chỉ seed khi bảng trống - tránh thêm trùng khi restart server
        if (tableRepository.count() > 0) return; // Already seeded

        TableType mainHall = findOrCreateType("Main Hall", 6);
        TableType vipRoom = findOrCreateType("VIP Room", 10);
        TableType patio = findOrCreateType("Patio", 6);

        List<RestaurantTable> tables = List.of(
            // ── Khu vực Main Hall (Sảnh chính) ────────────────────────────────
            build("T01", "Table 1", mainHall, 2),
            build("T02", "Table 2", mainHall, 2),
            build("T03", "Table 3", mainHall, 4),
            build("T04", "Table 4", mainHall, 4),
            build("T05", "Table 5", mainHall, 6),

            // ── Khu vực VIP Room ──────────────────────────────────────────────
            build("VIP-1", "VIP Room 1", vipRoom, 8),
            build("VIP-2", "VIP Room 2", vipRoom, 10),

            // ── Khu vực Patio (Sân ngoài) ─────────────────────────────────────
            build("P01", "Patio Table 1", patio, 4),
            build("P02", "Patio Table 2", patio, 4),
            build("P03", "Patio Table 3", patio, 6)
        );

        tableRepository.saveAll(tables);
        System.out.println("[TableSeeder] Seeded " + tables.size() + " restaurant tables.");
    }

    /**
     * Phương thức helper tạo một entity {@link RestaurantTable} từ các tham số.
     * Giúp code seed gọn gàng hơn thay vì phải set từng field cho mỗi bàn.
     * Bàn mới luôn được set:
     * - {@code status = AVAILABLE}
     * - {@code isActive = true}
     *
     * @param tableNumber Số bàn (ví dụ: T01, VIP-1...)
     * @param tableName   Tên bàn (ví dụ: "Table 1", "VIP Room 1"...)
     * @param tableType   Loại bàn (ví dụ: Main Hall, VIP Room, Patio...)
     * @param capacity    Sức chứa (số ghế)
     * @return Entity RestaurantTable đã được điền dữ liệu (chưa lưu DB)
     */
    private TableType findOrCreateType(String typeName, int capacity) {
        return tableTypeRepository.findByTypeNameIgnoreCase(typeName)
            .orElseGet(() -> {
                TableType tableType = new TableType();
                tableType.setTypeName(typeName);
                tableType.setCapacity(capacity);
                tableType.setStatus("ACTIVE");
                return tableTypeRepository.save(tableType);
            });
    }

    private RestaurantTable build(String tableNumber, String tableName, TableType tableType, Integer capacity) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(tableNumber);
        table.setTableName(tableName);
        table.setTableType(tableType);
        table.setCapacity(capacity);
        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
        table.setIsActive(true);
        return table;
    }
}
