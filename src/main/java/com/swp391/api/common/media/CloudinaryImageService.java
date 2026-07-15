package com.swp391.api.common.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloudinaryImageService {
    // Giới hạn được kiểm tra tại backend vì kiểm tra accept ở trình duyệt có thể bị bỏ qua.
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final Cloudinary cloudinary;
    private final String rootFolder;

    public CloudinaryImageService(Cloudinary cloudinary, @Value("${cloudinary.root-folder:golden-spoon}") String rootFolder) {
        this.cloudinary = cloudinary;
        this.rootFolder = rootFolder;
    }

    public ImageUploadResponse upload(MultipartFile file, String folder) {
        validate(file);

        // Chỉ cho phép ký tự an toàn trong tên thư mục để client không thể tạo đường dẫn tùy ý.
        String safeFolder = folder == null ? "general" : folder.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeFolder.isBlank()) safeFolder = "general";
        try {
            // unique_filename tránh ghi đè ảnh cũ khi hai file có cùng tên.
            // URL HTTPS và public_id từ kết quả upload sẽ được trả về cho frontend.
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", rootFolder + "/" + safeFolder, "resource_type", "image",
                    "use_filename", true, "unique_filename", true, "overwrite", false));
            return new ImageUploadResponse(String.valueOf(result.get("secure_url")), String.valueOf(result.get("public_id")),
                    number(result.get("bytes")), (int) number(result.get("width")),
                    (int) number(result.get("height")), String.valueOf(result.get("format")));
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not upload image to Cloudinary", exception);
        }
    }

    private void validate(MultipartFile file) {
        // Không tin filename/extension hoặc MIME do client gửi; chữ ký nhị phân cũng phải đúng định dạng khai báo.
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        if (file.getSize() > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must not exceed 10 MB");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, WebP, and GIF images are supported");
        if (!hasValidSignature(file, file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match its image type");
    }

    /** Chỉ chấp nhận URL phân phối ảnh HTTPS chính thức của Cloudinary. */
    public boolean isCloudinaryImageUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().contains("/image/upload/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasValidSignature(MultipartFile file, String contentType) {
        byte[] header = new byte[12];
        try (InputStream input = file.getInputStream()) {
            int length = input.read(header);
            if (length < 3) return false;
            return switch (contentType) {
                case "image/jpeg" -> unsigned(header[0]) == 0xFF && unsigned(header[1]) == 0xD8 && unsigned(header[2]) == 0xFF;
                case "image/png" -> length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P'
                        && header[2] == 'N' && header[3] == 'G' && unsigned(header[4]) == 0x0D
                        && unsigned(header[5]) == 0x0A && unsigned(header[6]) == 0x1A && unsigned(header[7]) == 0x0A;
                case "image/gif" -> length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                        && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
                case "image/webp" -> length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                        && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
                default -> false;
            };
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not inspect image file", exception);
        }
    }

    private int unsigned(byte value) { return value & 0xFF; }

    // Cloudinary trả metadata dưới dạng Map nên cần chuyển Number một cách an toàn.
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }
}
