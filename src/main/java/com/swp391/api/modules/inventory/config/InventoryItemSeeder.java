package com.swp391.api.modules.inventory.config;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component khởi tạo dữ liệu mẫu cho bảng {@code inventory_items} khi ứng dụng khởi động.
 *
 * <p>Chạy tự động khi ứng dụng start vì implement {@link CommandLineRunner}.
 * Chỉ thêm dữ liệu khi bảng trống (count == 0) để tránh trùng lặp khi restart.</p>
 *
 * <p>{@code @Order(2)} đảm bảo seeder này chạy sau {@code DefaultUsersSeeder} (Order 1),
 * vì inventory không phụ thuộc user nhưng cần giữ thứ tự nhất quán.</p>
 *
 * <p>Sử dụng ảnh Unsplash thực tế để demo có hình ảnh đẹp trên UI.</p>
 *
 * Seeds sample inventory items on startup if the table is empty.
 * Uses real Unsplash food images for a realistic demo.
 */
@Component
@Order(2)
public class InventoryItemSeeder implements CommandLineRunner {

    /** Repository để lưu dữ liệu seed vào DB */
    private final InventoryRepository inventoryRepository;

    /**
     * Constructor injection InventoryRepository.
     *
     * @param inventoryRepository Repository để thao tác với bảng inventory_items
     */
    public InventoryItemSeeder(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Phương thức thực thi khi ứng dụng khởi động.
     * Kiểm tra nếu bảng đã có dữ liệu thì bỏ qua, không seed lại.
     * Tạo danh sách 15 mặt hàng mẫu đại diện cho các nhóm nguyên liệu khác nhau.
     *
     * @param args Tham số command line (không dùng trong trường hợp này)
     */
    @Override
    public void run(String... args) {
        // Chỉ seed khi bảng trống - tránh thêm trùng khi restart server
        if (inventoryRepository.count() > 0) return; // Already seeded

        List<InventoryItem> items = List.of(
            // ── Nhóm Rau củ (Vegetables) ──────────────────────────────────────────
            build("Fresh Tomatoes",      "Vegetables", "kg",   48.0,  10.0, 25000.0,  "Fresh Farm Co.",
                  "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=300&q=80"),

            build("Lettuce",             "Vegetables", "kg",    6.5,   5.0, 18000.0,  "Green Garden Supply",
                  "https://images.unsplash.com/photo-1622205313162-be1d5712a43f?w=300&q=80"),

            build("White Onion",         "Vegetables", "kg",   30.0,   8.0, 12000.0,  "Fresh Farm Co.",
                  "https://images.unsplash.com/photo-1508747703725-719777637510?w=300&q=80"),

            build("Garlic",              "Vegetables", "kg",    4.0,   3.0, 55000.0,  "Fresh Farm Co.",
                  "https://images.unsplash.com/photo-1615811648503-479d06917e97?w=300&q=80"),

            // ── Nhóm Thịt (Meat) ───────────────────────────────────────────────────
            build("Chicken Breast",      "Meat",       "kg",   25.0,  10.0, 89000.0,  "Premium Meat Co.",
                  "https://images.unsplash.com/photo-1604503468506-a8da13d11b73?w=300&q=80"),

            build("Beef Tenderloin",     "Meat",       "kg",    3.5,   5.0, 320000.0, "Premium Meat Co.",
                  "https://images.unsplash.com/photo-1603048297172-c92544798d5a?w=300&q=80"),

            // Pork Ribs có quantity = 0.0 → trạng thái tự động sẽ là OUT_OF_STOCK
            build("Pork Ribs",           "Meat",       "kg",    0.0,   4.0, 110000.0, "Premium Meat Co.",
                  "https://images.unsplash.com/photo-1544025162-d76694265947?w=300&q=80"),

            // ── Nhóm Hải sản (Seafood) ─────────────────────────────────────────────
            build("Salmon Fillet",       "Seafood",    "kg",   12.0,   5.0, 280000.0, "Ocean Fresh Ltd.",
                  "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=300&q=80"),

            // Tiger Prawns có quantity = 2.5 <= minimumQuantity = 4.0 → tự động LOW_STOCK
            build("Tiger Prawns",        "Seafood",    "kg",    2.5,   4.0, 350000.0, "Ocean Fresh Ltd.",
                  "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=300&q=80"),

            // ── Nhóm Sữa và chế phẩm (Dairy) ──────────────────────────────────────
            build("Fresh Milk",          "Dairy",      "lít",  18.0,   8.0, 28000.0,  "Vinamilk",
                  "https://images.unsplash.com/photo-1563636619-e9143da7973b?w=300&q=80"),

            build("Unsalted Butter",     "Dairy",      "kg",    3.5,   2.0, 95000.0,  "Vinamilk",
                  "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d?w=300&q=80"),

            // ── Nhóm Ngũ cốc (Grains) ──────────────────────────────────────────────
            build("Jasmine Rice",        "Grains",     "kg",   80.0,  20.0, 22000.0,  "Agrifood Vietnam",
                  "https://images.unsplash.com/photo-1536304993881-ff86e0c9c849?w=300&q=80"),

            // ── Nhóm Gia vị (Spices) ───────────────────────────────────────────────
            build("Black Pepper",        "Spices",     "gram", 500.0, 100.0, 350.0,   "Spice World",
                  "https://images.unsplash.com/photo-1596040033229-a9821ebd058d?w=300&q=80"),

            build("Fish Sauce",          "Spices",     "chai",  24.0,   6.0, 35000.0, "Phu Quoc Fish Sauce",
                  "https://images.unsplash.com/photo-1617196034183-421b4040ed20?w=300&q=80"),

            // ── Nhóm Đồ uống (Beverages) ───────────────────────────────────────────
            build("Sparkling Water",     "Beverages",  "chai",  60.0,  24.0, 12000.0, "Lavie",
                  "https://images.unsplash.com/photo-1548839140-29a749e1cf4d?w=300&q=80")
        );

        // Lưu tất cả mặt hàng vào DB trong một lần (batch insert)
        inventoryRepository.saveAll(items);
        System.out.println("[InventorySeeder] Seeded " + items.size() + " inventory items.");
    }

    /**
     * Phương thức helper tạo một entity {@link InventoryItem} từ các tham số.
     * Giúp code seed gọn gàng hơn thay vì phải set từng field cho mỗi item.
     * Mặt hàng mới luôn được set {@code isActive = true}.
     *
     * @param name      Tên mặt hàng
     * @param category  Danh mục
     * @param unit      Đơn vị tính
     * @param qty       Số lượng ban đầu
     * @param minQty    Ngưỡng tối thiểu
     * @param price     Giá mỗi đơn vị
     * @param supplier  Nhà cung cấp
     * @param imageUrl  URL ảnh đại diện
     * @return Entity InventoryItem đã được điền dữ liệu (chưa lưu DB)
     */
    private InventoryItem build(String name, String category, String unit,
                                 double qty, double minQty, double price,
                                 String supplier, String imageUrl) {
        InventoryItem item = new InventoryItem();
        item.setItemName(name);
        item.setCategory(category);
        item.setUnit(unit);
        item.setQuantity(qty);
        item.setMinimumQuantity(minQty);
        item.setPricePerUnit(price);
        item.setSupplier(supplier);
        item.setImageUrl(imageUrl);
        item.setIsActive(true); // Tất cả mặt hàng seed đều được kích hoạt mặc định
        return item;
    }
}
