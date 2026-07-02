package com.swp391.api.modules.inventory.service.impl;

import com.swp391.api.modules.inventory.dto.InventoryItemRequest;
import com.swp391.api.modules.inventory.dto.InventoryItemResponse;
import com.swp391.api.modules.inventory.dto.UpdateInventoryItemRequest;
import com.swp391.api.modules.inventory.dto.UpdateQuantityRequest;
import com.swp391.api.modules.inventory.dto.UpdateStatusRequest;
import com.swp391.api.modules.inventory.entity.InventoryItem;
import com.swp391.api.modules.inventory.repository.InventoryRepository;
import com.swp391.api.modules.inventory.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation của {@link InventoryService} - xử lý toàn bộ nghiệp vụ quản lý kho.
 *
 * <p>Annotation {@code @Service} đánh dấu class này là Spring Bean thuộc tầng service.
 * Annotation {@code @Transactional} đảm bảo mỗi method thay đổi dữ liệu chạy trong
 * một transaction riêng - nếu có lỗi giữa chừng, dữ liệu sẽ được rollback.</p>
 */
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    /** Repository để truy cập database bảng inventory_items */
    private final InventoryRepository inventoryRepository;

    /**
     * Constructor injection - Spring tự động inject InventoryRepository.
     * Dùng constructor injection thay vì @Autowired để dễ test (mock dependency).
     *
     * @param inventoryRepository Repository quản lý dữ liệu kho
     */
    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // ─── Tính toán trạng thái ────────────────────────────────────────────────────
    /**
     * Tính toán trạng thái tồn kho dựa theo ưu tiên:
     * 1. Nếu có ghi đè thủ công (statusOverride != null) → dùng giá trị ghi đè
     * 2. Tự động tính dựa trên ngưỡng số lượng:
     *    - OUT_OF_STOCK : quantity <= 0 (hết hàng)
     *    - LOW_STOCK    : 0 < quantity <= minimumQuantity (sắp hết)
     *    - IN_STOCK     : quantity > minimumQuantity (còn hàng đủ)
     *
     * Status priority:
     * 1. Manual override (statusOverride != null) → use override value
     * 2. Auto-calculate based on quantity thresholds:
     *    - OUT_OF_STOCK : quantity <= 0
     *    - LOW_STOCK    : 0 < quantity <= minimumQuantity
     *    - IN_STOCK     : quantity > minimumQuantity
     *
     * @param item Entity mặt hàng cần tính trạng thái
     * @return Chuỗi trạng thái: "IN_STOCK", "LOW_STOCK", hoặc "OUT_OF_STOCK"
     */
    private String computeStatus(InventoryItem item) {
        // Ưu tiên 1: Nếu quản lý đã ghi đè thủ công thì dùng luôn
        if (item.getStatusOverride() != null) return item.getStatusOverride();
        double availableQuantity = Math.max(0.0, item.getQuantity() - item.getReservedQuantity());
        // Ưu tiên 2: Hết hàng khi số lượng = 0
        if (availableQuantity <= 0) return "OUT_OF_STOCK";
        // Ưu tiên 3: Sắp hết khi số lượng <= ngưỡng tối thiểu
        if (availableQuantity <= item.getMinimumQuantity()) return "LOW_STOCK";
        // Mặc định: còn hàng đủ
        return "IN_STOCK";
    }

    // ─── Chuyển đổi Entity → Response DTO ────────────────────────────────────────
    /**
     * Chuyển đổi entity {@link InventoryItem} sang DTO {@link InventoryItemResponse}.
     * Phương thức này tập trung logic mapping để tránh lặp code ở các service method.
     * Cũng tính toán trường {@code status} và {@code isStatusOverridden} từ entity.
     *
     * @param item Entity cần chuyển đổi
     * @return DTO response sẵn sàng trả về client
     */
    private InventoryItemResponse toResponse(InventoryItem item) {
        InventoryItemResponse response = new InventoryItemResponse();
        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setCategory(item.getCategory());
        response.setUnit(item.getUnit());
        response.setQuantity(item.getQuantity());
        response.setReservedQuantity(item.getReservedQuantity());
        response.setAvailableQuantity(Math.max(0.0, item.getQuantity() - item.getReservedQuantity()));
        response.setMinimumQuantity(item.getMinimumQuantity());
        response.setPricePerUnit(item.getPricePerUnit());
        response.setSupplier(item.getSupplier());
        response.setIsActive(item.getIsActive());
        response.setImageUrl(item.getImageUrl());
        // Tính trạng thái cuối cùng (tự động hoặc override)
        response.setStatus(computeStatus(item));
        // Cho client biết trạng thái này là thủ công hay tự động
        response.setIsStatusOverridden(item.getStatusOverride() != null);
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }

    // ─── Các phương thức nghiệp vụ ───────────────────────────────────────────────

    /**
     * Lấy toàn bộ danh sách mặt hàng kho.
     * {@code @Transactional(readOnly = true)} tối ưu hiệu năng cho thao tác chỉ đọc
     * (Hibernate không cần dirty-checking, connection pool được dùng hiệu quả hơn).
     *
     * @return Danh sách tất cả mặt hàng
     */
    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getAllItems() {
        return inventoryRepository.findAll()
                .stream()
                .map(this::toResponse)  // Chuyển mỗi entity sang DTO
                .collect(Collectors.toList());
    }

    /**
     * Lấy thông tin một mặt hàng theo ID.
     * Ném 404 nếu không tìm thấy để client biết ID không hợp lệ.
     *
     * @param id ID mặt hàng
     * @return Thông tin mặt hàng
     * @throws ResponseStatusException 404 nếu không tìm thấy
     */
    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(Long id) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
        return toResponse(item);
    }

    /**
     * Tạo mới mặt hàng kho.
     * Kiểm tra trùng tên (case-insensitive) trước khi tạo để đảm bảo unique constraint.
     * Mặt hàng mới luôn được set isActive = true (đang hoạt động).
     *
     * @param request Dữ liệu mặt hàng mới
     * @return Mặt hàng vừa tạo
     * @throws ResponseStatusException 409 nếu tên mặt hàng đã tồn tại
     */
    @Override
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        // Kiểm tra trùng tên trước khi tạo (không phân biệt hoa thường)
        inventoryRepository.findByItemNameIgnoreCase(request.getItemName())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Item name already exists");
                });

        // Tạo entity mới từ dữ liệu request
        InventoryItem item = new InventoryItem();
        item.setItemName(request.getItemName());
        item.setCategory(request.getCategory());
        item.setUnit(request.getUnit());
        item.setQuantity(request.getQuantity());
        item.setMinimumQuantity(request.getMinimumQuantity());
        item.setPricePerUnit(request.getPricePerUnit());
        item.setSupplier(request.getSupplier());
        item.setImageUrl(request.getImageUrl());
        item.setIsActive(true); // Mặt hàng mới luôn active

        InventoryItem saved = inventoryRepository.save(item);
        return toResponse(saved);
    }

    /**
     * Chỉnh sửa các thông tin vận hành của mặt hàng mà không thay đổi tên hoặc ảnh.
     */
    @Override
    public InventoryItemResponse updateItem(Long id, UpdateInventoryItemRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        if (request.getQuantity() < item.getReservedQuantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quantity cannot be lower than reserved quantity (" + item.getReservedQuantity() + ")"
            );
        }

        item.setCategory(request.getCategory());
        item.setUnit(request.getUnit());
        item.setQuantity(request.getQuantity());
        item.setMinimumQuantity(request.getMinimumQuantity());
        item.setPricePerUnit(request.getPricePerUnit());
        item.setSupplier(request.getSupplier());

        return toResponse(inventoryRepository.save(item));
    }

    /**
     * Cập nhật số lượng tồn kho cho mặt hàng.
     * Số lượng mới sẽ THAY THẾ hoàn toàn số lượng cũ (không phải cộng thêm).
     * Sau khi cập nhật, trạng thái sẽ được tính lại tự động (nếu không có override).
     *
     * @param id      ID mặt hàng cần cập nhật
     * @param request Số lượng mới
     * @return Mặt hàng sau khi cập nhật
     * @throws ResponseStatusException 404 nếu không tìm thấy mặt hàng
     */
    @Override
    public InventoryItemResponse updateQuantity(Long id, UpdateQuantityRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        if (request.getQuantity() < item.getReservedQuantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quantity cannot be lower than reserved quantity (" + item.getReservedQuantity() + ")"
            );
        }

        // Thay thế số lượng hoàn toàn bằng giá trị mới
        item.setQuantity(request.getQuantity());
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    /**
     * Ghi đè thủ công trạng thái mặt hàng, hoặc reset về tính tự động.
     * Validate giá trị status trước khi lưu để tránh dữ liệu sai vào DB.
     *
     * @param id      ID mặt hàng cần cập nhật trạng thái
     * @param request Trạng thái mới (có thể null để reset)
     * @return Mặt hàng sau khi cập nhật
     * @throws ResponseStatusException 404 nếu không tìm thấy mặt hàng
     * @throws ResponseStatusException 400 nếu giá trị status không hợp lệ
     */
    @Override
    public InventoryItemResponse updateStatus(Long id, UpdateStatusRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        String override = request.getStatusOverride();
        // Validate nếu giá trị không phải null - chỉ chấp nhận 3 giá trị hợp lệ
        if (override != null && !override.equals("IN_STOCK")
                && !override.equals("LOW_STOCK")
                && !override.equals("OUT_OF_STOCK")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status. Must be IN_STOCK, LOW_STOCK, OUT_OF_STOCK, or null to reset.");
        }

        item.setStatusOverride(override); // null = reset về tính tự động
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    /**
     * Bật/tắt trạng thái hoạt động của mặt hàng (toggle).
     * Đây là cơ chế soft delete: thay vì xóa hẳn, ta chỉ đánh dấu inactive.
     * Giúp giữ lại lịch sử và có thể khôi phục sau này.
     *
     * @param id ID mặt hàng cần toggle
     * @return Mặt hàng sau khi toggle trạng thái
     * @throws ResponseStatusException 404 nếu không tìm thấy mặt hàng
     */
    @Override
    public InventoryItemResponse toggleActive(Long id) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        // Đảo ngược trạng thái: true → false hoặc false → true
        item.setIsActive(!item.getIsActive());
        InventoryItem updated = inventoryRepository.save(item);
        return toResponse(updated);
    }

    /**
     * Tìm kiếm mặt hàng với nhiều bộ lọc kết hợp.
     * Logic xây dựng query động dựa theo bộ lọc nào được cung cấp,
     * tránh dùng JPQL phức tạp bằng cách dùng derived query của Spring Data.
     *
     * <p>Thứ tự ưu tiên từ cụ thể nhất đến tổng quát nhất:
     * keyword + category + isActive → keyword + category → keyword + isActive
     * → chỉ keyword → chỉ category → chỉ isActive → không có điều kiện = lấy tất cả</p>
     *
     * @param keyword  Từ khóa tìm theo tên (null = bỏ qua)
     * @param category Danh mục cần lọc (null = bỏ qua)
     * @param isActive Trạng thái hoạt động (null = bỏ qua)
     * @return Danh sách mặt hàng thỏa điều kiện
     */
    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> searchItems(String keyword, String category, Boolean isActive) {
        List<InventoryItem> results;

        // Kiểm tra từng bộ lọc có được cung cấp và có giá trị hợp lệ không
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasActive = isActive != null;

        // Chọn query phù hợp dựa trên tổ hợp bộ lọc được cung cấp
        if (hasKeyword && hasCategory && hasActive) {
            // Đầy đủ 3 điều kiện - query cụ thể nhất
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndCategoryIgnoreCaseAndIsActive(keyword, category, isActive);
        } else if (hasKeyword && hasCategory) {
            // Keyword + Category
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndCategoryIgnoreCase(keyword, category);
        } else if (hasKeyword && hasActive) {
            // Keyword + trạng thái hoạt động
            results = inventoryRepository
                    .findByItemNameContainingIgnoreCaseAndIsActive(keyword, isActive);
        } else if (hasKeyword) {
            // Chỉ keyword
            results = inventoryRepository.findByItemNameContainingIgnoreCase(keyword);
        } else if (hasCategory) {
            // Chỉ category
            results = inventoryRepository.findByCategoryIgnoreCase(category);
        } else if (hasActive) {
            // Chỉ trạng thái hoạt động
            results = inventoryRepository.findByIsActive(isActive);
        } else {
            // Không có điều kiện nào → lấy tất cả (giống getAllItems)
            results = inventoryRepository.findAll();
        }

        return results.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
