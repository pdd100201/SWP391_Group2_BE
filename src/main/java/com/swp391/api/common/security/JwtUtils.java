package com.swp391.api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class cung cấp các phương thức xử lý JWT (JSON Web Token).
 *
 * <p>Chịu trách nhiệm:
 * <ul>
 *   <li>Tạo JWT token khi đăng nhập thành công</li>
 *   <li>Xác thực token từ request</li>
 *   <li>Trích xuất thông tin (subject, role) từ token</li>
 * </ul>
 * </p>
 *
 * <p>{@code @Component} để Spring quản lý như một Bean,
 * giúp inject vào các class khác qua constructor hoặc @Autowired.</p>
 *
 * <p>Secret key và thời gian hết hạn được cấu hình trong {@code application.properties}
 * thông qua {@code @Value} - không hard-code để đảm bảo bảo mật.</p>
 */
@Component
public class JwtUtils {

    /** Secret key được tạo từ chuỗi bí mật cấu hình - dùng để ký và xác thực JWT */
    private final SecretKey secretKey;

    /** Thời gian sống của token tính bằng milliseconds (lấy từ application.properties) */
    private final long expirationMillis;

    /**
     * Constructor - khởi tạo secret key từ cấu hình.
     * Thử decode Base64 trước (chuẩn), nếu thất bại thì dùng bytes UTF-8 trực tiếp.
     * Điều này giúp hỗ trợ cả hai dạng cấu hình secret key.
     *
     * @param secret          Chuỗi bí mật lấy từ {@code jwt.secret} trong config
     * @param expirationMillis Thời gian sống token (ms) từ {@code jwt.expiration}
     */
    public JwtUtils(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMillis) {
        byte[] keyBytes;
        try {
            // Thử decode Base64 - đây là cách chuẩn để cấu hình secret key
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            // Fallback: dùng chuỗi raw UTF-8 nếu không phải Base64
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        // Tạo HMAC-SHA key từ bytes - đảm bảo key đủ mạnh cho HS256
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    /**
     * Tạo JWT token chứa thông tin người dùng.
     * Token được ký bằng thuật toán HMAC-SHA256 (HS256) với secret key.
     *
     * <p>Cấu trúc payload:
     * <ul>
     *   <li>{@code sub} (subject): email hoặc định danh người dùng</li>
     *   <li>{@code role}: vai trò (ADMIN, MANAGER, CUSTOMER...)</li>
     *   <li>{@code iat}: thời điểm phát hành token</li>
     *   <li>{@code exp}: thời điểm token hết hạn</li>
     * </ul>
     * </p>
     *
     * @param subject Định danh người dùng (thường là email)
     * @param role    Vai trò của người dùng (ADMIN, MANAGER, CUSTOMER...)
     * @return Chuỗi JWT token đã được ký
     */
    public String generateToken(String subject, String role) {
        Date now = new Date();
        // Tính thời điểm hết hạn = hiện tại + khoảng thời gian cấu hình
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(subject)           // Đặt subject (email người dùng)
                .claim("role", role)        // Thêm claim role vào payload
                .issuedAt(now)              // Thời điểm phát hành
                .expiration(expiry)         // Thời điểm hết hạn
                .signWith(secretKey, SignatureAlgorithm.HS256)  // Ký token bằng HMAC-SHA256
                .compact();                 // Nén thành chuỗi JWT
    }

    /**
     * Kiểm tra token có hợp lệ không (chữ ký đúng, chưa hết hạn, không bị can thiệp).
     * Dùng trong filter để quyết định có tiếp tục xử lý request hay từ chối.
     *
     * @param token Chuỗi JWT token cần kiểm tra
     * @return {@code true} nếu token hợp lệ, {@code false} nếu không hợp lệ hoặc hết hạn
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token); // Nếu parse thành công thì token hợp lệ
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException: chữ ký sai, hết hạn, bị can thiệp...
            // IllegalArgumentException: token rỗng hoặc null
            return false;
        }
    }

    /**
     * Trích xuất subject (email người dùng) từ JWT token.
     * Dùng để xác định người dùng nào đang gửi request.
     *
     * @param token Chuỗi JWT token (đã được validate trước đó)
     * @return Subject (email) chứa trong token
     */
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Trích xuất role (vai trò) của người dùng từ JWT token.
     * Dùng để xác định quyền truy cập của người dùng vào các endpoint.
     *
     * @param token Chuỗi JWT token (đã được validate trước đó)
     * @return Role (ADMIN, MANAGER, CUSTOMER...) chứa trong token
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Parse JWT token và trả về toàn bộ Claims (payload).
     * Phương thức private - chỉ dùng nội bộ trong class này.
     * Sẽ ném exception nếu token không hợp lệ, hết hạn hoặc bị can thiệp.
     *
     * @param token Chuỗi JWT cần parse
     * @return Claims chứa toàn bộ thông tin trong payload của token
     * @throws JwtException nếu token không hợp lệ
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)          // Xác minh chữ ký bằng secret key
                .build()
                .parseSignedClaims(token)       // Parse và xác thực token
                .getPayload();                  // Lấy phần payload (Claims)
    }
}
