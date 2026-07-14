package com.swp391.api.common.media;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudinaryImageService {

    public record UploadResult(String url) {}

    public UploadResult upload(MultipartFile file, String folder) {
        throw new UnsupportedOperationException("Image upload service not configured");
    }
}
