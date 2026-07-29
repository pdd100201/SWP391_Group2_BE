package com.swp391.api.modules.menu.service.impl;

import com.swp391.api.common.media.CloudinaryImageService;
import com.swp391.api.modules.menu.dto.MenuItemRequest;
import com.swp391.api.modules.menu.dto.MenuItemResponse;
import com.swp391.api.modules.menu.entity.MenuCategory;
import com.swp391.api.modules.menu.entity.MenuItem;
import com.swp391.api.modules.menu.repository.MenuCategoryRepository;
import com.swp391.api.modules.menu.repository.MenuItemRepository;
import com.swp391.api.modules.menu.service.MenuService;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Triển khai toàn bộ quy tắc nghiệp vụ của quản lý Menu.
 * @Transactional bảo đảm các thay đổi database trong mỗi hàm hoàn tất cùng nhau;
 * nếu phát sinh exception thì dữ liệu của transaction sẽ được hoàn tác.
 */
@Service
@Transactional
public class MenuServiceImpl implements MenuService {
    // Repository món ăn chịu trách nhiệm đọc/ghi bảng restaurant_menu_items.
    private final MenuItemRepository menuItemRepository;

    // Repository danh mục dùng để xác minh món luôn thuộc một danh mục đang hoạt động.
    private final MenuCategoryRepository categoryRepository;

    // Service ảnh dùng để xác minh URL do frontend gửi thực sự thuộc Cloudinary.
    private final CloudinaryImageService imageService;

    public MenuServiceImpl(MenuItemRepository menuItemRepository, MenuCategoryRepository categoryRepository,
            CloudinaryImageService imageService) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.imageService = imageService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAll() {
        // Chế độ readOnly cho biết hàm này chỉ đọc dữ liệu và không cập nhật database.
        // Entity không được trả thẳng ra API; mỗi entity được chuyển thành MenuItemResponse.
        return menuItemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getById(Long id) { return toResponse(findMenuItem(id)); }

    @Override
    public MenuItemResponse create(MenuItemRequest request) {
        // Tên món phải duy nhất. trim loại khoảng trắng thừa và IgnoreCase bỏ qua hoa/thường.
        menuItemRepository.findByNameIgnoreCase(request.getName().trim()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish name already exists");
        });

        // Tạo entity mới, sao chép dữ liệu hợp lệ và mặc định cho phép phục vụ ngay.
        MenuItem item = new MenuItem();
        applyRequest(item, request);
        item.setIsActive(true);

        // save sinh lệnh INSERT; entity đã lưu được chuyển thành DTO trước khi trả về controller.
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public MenuItemResponse update(Long id, MenuItemRequest request) {
        // Chỉ cho cập nhật món đang tồn tại; findMenuItem trả 404 nếu không tìm thấy ID.
        MenuItem item = findMenuItem(id);

        // Cho phép giữ tên của chính món hiện tại, nhưng không được trùng với ID của món khác.
        menuItemRepository.findByNameIgnoreCase(request.getName().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish name already exists"); });

        // Cập nhật các trường được phép thay đổi rồi sinh lệnh UPDATE qua repository.
        applyRequest(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Override
    public MenuItemResponse toggleActive(Long id) {
        // Không xóa cứng món khỏi database vì các Order cũ có thể đang tham chiếu đến món này.
        // Thao tác chỉ đảo true thành false hoặc false thành true.
        MenuItem item = findMenuItem(id);
        item.setIsActive(!item.getIsActive());
        return toResponse(menuItemRepository.save(item));
    }

    private void applyRequest(MenuItem item, MenuItemRequest request) {
        // Frontend upload ảnh trước và chỉ gửi secure_url khi lưu món.
        // Kiểm tra lại ở backend để chặn URL giả hoặc URL từ một website bên ngoài.
        if (!imageService.isCloudinaryImageUrl(request.getImageUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dish image URL must belong to Cloudinary");
        }

        // Chuẩn hóa chuỗi, tìm đúng entity danh mục và gán giá bán trực tiếp cho món.
        item.setName(request.getName().trim());
        item.setMenuCategory(resolveCategory(request));
        item.setDescription(normalizeNullable(request.getDescription()));
        item.setImageUrl(normalizeNullable(request.getImageUrl()));
        item.setPrice(request.getPrice());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        // Chỉ sao chép các trường API cần; không để frontend phụ thuộc trực tiếp vào entity JPA.
        MenuItemResponse response = new MenuItemResponse();
        response.setId(item.getId());
        response.setName(item.getName());
        response.setCategory(item.getCategory());
        response.setCategoryId(item.getMenuCategory() == null ? null : item.getMenuCategory().getId());
        response.setDescription(item.getDescription());
        response.setImageUrl(item.getImageUrl());
        response.setPrice(item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());
        response.setIsActive(item.getIsActive());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());

        // Module hiện không tính tồn kho; trạng thái hiển thị chỉ phụ thuộc isActive.
        response.setAvailability(Boolean.TRUE.equals(item.getIsActive()) ? "AVAILABLE" : "INACTIVE");
        return response;
    }

    private MenuItem findMenuItem(Long id) {
        // Gom logic tìm theo ID tại một chỗ để update, toggle và getById dùng chung thông báo lỗi.
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    private MenuCategory resolveCategory(MenuItemRequest request) {
        // Ưu tiên categoryId vì ID ổn định hơn tên; vẫn hỗ trợ tên để tương thích frontend hiện tại.
        // Dù tìm theo cách nào, danh mục bắt buộc phải đang hoạt động.
        if (request.getCategoryId() != null) {
            return categoryRepository.findById(request.getCategoryId())
                    .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
        }
        return categoryRepository.findByNameIgnoreCase(request.getCategory().trim())
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active menu category not found"));
    }

    // Chuỗi rỗng được đổi thành null; chuỗi có nội dung được loại khoảng trắng ở hai đầu.
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
