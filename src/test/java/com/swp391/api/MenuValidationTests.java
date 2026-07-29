package com.swp391.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudinary.Cloudinary;
import com.swp391.api.common.media.CloudinaryImageService;
import com.swp391.api.modules.menu.dto.MenuItemRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Kiểm tra các quy tắc validation quan trọng của món ăn và file ảnh.
 * Các test này giúp phát hiện sớm khi một thay đổi vô tình cho phép dữ liệu không hợp lệ đi qua.
 */
class MenuValidationTests {
    // Validator thật của Jakarta được dùng giống cơ chế @Valid khi controller nhận request.
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void menuRequiresPositiveIntegerPriceDescriptionAndImage() {
        // Bắt đầu bằng request hợp lệ, sau đó cố ý làm sai ba trường để kiểm tra đúng tên field lỗi.
        MenuItemRequest request = validRequest();
        assertTrue(validator.validate(request).isEmpty());

        request.setPrice(new BigDecimal("-1"));
        request.setDescription("word ".repeat(201));
        request.setImageUrl(" ");
        var fields = validator.validate(request).stream()
                .map(error -> error.getPropertyPath().toString()).collect(Collectors.toSet());
        assertTrue(fields.contains("price"));
        assertTrue(fields.contains("description"));
        assertTrue(fields.contains("imageUrl"));
    }

    @Test
    void menuRejectsFractionalPrice() {
        // Giá bán trong dự án là VND nguyên nên giá có phần thập phân phải bị từ chối.
        MenuItemRequest request = validRequest();
        request.setPrice(new BigDecimal("1500.50"));
        assertFalse(validator.validateProperty(request, "price").isEmpty());
    }

    @Test
    void cloudinaryValidationRejectsExternalUrlsAndFakeImages() {
        // URL Cloudinary hợp lệ được chấp nhận, URL ngoài hệ thống và file giả phải bị từ chối.
        CloudinaryImageService service = new CloudinaryImageService(new Cloudinary(), "golden-spoon");
        assertTrue(service.isCloudinaryImageUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg"));
        assertFalse(service.isCloudinaryImageUrl("https://example.com/sample.jpg"));

        MockMultipartFile fakePng = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.upload(fakePng, "menu"));
    }

    private MenuItemRequest validRequest() {
        // Tạo dữ liệu chuẩn dùng chung để mỗi test chỉ cần thay field đang muốn kiểm tra.
        MenuItemRequest request = new MenuItemRequest();
        request.setName("Test dish");
        request.setCategory("Main Course");
        request.setDescription("A complete and valid dish description.");
        request.setImageUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg");
        request.setPrice(new BigDecimal("150000"));
        return request;
    }
}
