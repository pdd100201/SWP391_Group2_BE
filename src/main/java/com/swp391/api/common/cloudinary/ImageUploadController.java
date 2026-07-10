package com.swp391.api.common.cloudinary;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads/images")
public class ImageUploadController {
    private final CloudinaryStorageService cloudinaryStorageService;

    public ImageUploadController(CloudinaryStorageService cloudinaryStorageService) {
        this.cloudinaryStorageService = cloudinaryStorageService;
    }

    @PostMapping
    public ResponseEntity<ImageUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder) {
        return ResponseEntity.ok(cloudinaryStorageService.uploadImage(file, folder));
    }
}
