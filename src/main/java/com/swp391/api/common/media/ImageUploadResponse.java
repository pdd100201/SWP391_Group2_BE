package com.swp391.api.common.media;

/**
 * Kết quả upload ảnh trả cho frontend:
 * URL dùng để hiển thị/lưu database; publicId dùng để quản lý file trên Cloudinary;
 * các trường còn lại mô tả dung lượng, kích thước và định dạng ảnh.
 */
public record ImageUploadResponse(String url, String publicId, long bytes, int width, int height, String format) {}
