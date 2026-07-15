package com.swp391.api.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {
    /**
     * Khởi tạo một Cloudinary client dùng chung cho toàn ứng dụng.
     * Thông tin xác thực được lấy từ application.yaml/biến môi trường, không ghi cứng
     * API secret trong source code. Cờ secure buộc URL trả về sử dụng HTTPS.
     */
    @Bean
    public Cloudinary cloudinary(@Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "api_key", apiKey,
                "api_secret", apiSecret, "secure", true));
    }
}
