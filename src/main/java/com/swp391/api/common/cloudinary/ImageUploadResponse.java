package com.swp391.api.common.cloudinary;

public record ImageUploadResponse(
        String url,
        String secureUrl,
        String publicId,
        String originalFilename) {
}
