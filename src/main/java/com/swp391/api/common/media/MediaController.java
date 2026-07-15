package com.swp391.api.common.media;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final CloudinaryImageService imageService;
    public MediaController(CloudinaryImageService imageService) { this.imageService = imageService; }

    /**
     * Nhận multipart/form-data từ frontend và ủy quyền toàn bộ validate/upload cho service.
     * folder chỉ là thư mục con nghiệp vụ (ví dụ menu hoặc avatars), không phải đường dẫn đầy đủ.
     */
    @PostMapping("/images")
    public ResponseEntity<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String folder) {
        return ResponseEntity.ok(imageService.upload(file, folder));
    }
}
