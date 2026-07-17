package com.swp391.api.common.media;

public record ImageUploadResponse(String url, String publicId, long bytes, int width, int height, String format) {}
