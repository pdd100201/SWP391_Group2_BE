package com.swp391.api.common.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloudinaryImageService {

    private final Cloudinary cloudinary;
    private final String rootFolder;

    public CloudinaryImageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.root-folder:golden-spoon}") String rootFolder) {
        this.rootFolder = rootFolder;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public UploadedImage upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", buildFolder(folder),
                    "resource_type", "image"
            ));
            return new UploadedImage(
                    String.valueOf(result.get("secure_url")),
                    String.valueOf(result.get("public_id"))
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read image file", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to upload image", ex);
        }
    }

    private String buildFolder(String folder) {
        String cleanRoot = trimSlashes(rootFolder);
        String cleanFolder = trimSlashes(folder);
        if (cleanRoot.isBlank()) {
            return cleanFolder;
        }
        if (cleanFolder.isBlank()) {
            return cleanRoot;
        }
        return cleanRoot + "/" + cleanFolder;
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^/+|/+$", "");
    }

    public record UploadedImage(String url, String publicId) {
    }
}
