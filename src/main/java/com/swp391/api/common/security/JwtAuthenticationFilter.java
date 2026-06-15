package com.swp391.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter xác thực JWT - chạy một lần duy nhất trước mỗi HTTP request.
 *
 * <p>Kế thừa {@link OncePerRequestFilter} đảm bảo filter chỉ được gọi một lần
 * cho mỗi request, ngay cả trong trường hợp request dispatch nội bộ.</p>
 *
 * <p>Luồng xử lý:
 * <ol>
 *   <li>Đọc header {@code Authorization} từ request</li>
 *   <li>Nếu có token dạng "Bearer {token}" thì trích xuất và validate</li>
 *   <li>Nếu token hợp lệ, tạo Authentication object và lưu vào SecurityContext</li>
 *   <li>Tiếp tục cho request đi qua filter chain</li>
 * </ol>
 * </p>
 *
 * <p>Nếu không có token hoặc token không hợp lệ, request vẫn tiếp tục nhưng
 * SecurityContext sẽ không có authentication → Spring Security sẽ từ chối
 * các endpoint yêu cầu xác thực.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Utility class để validate và parse JWT token */
    private final JwtUtils jwtUtils;

    /**
     * Constructor injection JwtUtils.
     *
     * @param jwtUtils Utility xử lý JWT
     */
    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * Logic xử lý chính của filter - được Spring Security gọi cho mỗi request.
     *
     * <p>Chỉ xử lý token nếu header Authorization có dạng "Bearer {token}".
     * Nếu token hợp lệ, thiết lập Authentication vào SecurityContext để
     * các tầng tiếp theo (controller, service) biết request này đã được xác thực.</p>
     *
     * @param request     HTTP request đang được xử lý
     * @param response    HTTP response để ghi kết quả
     * @param filterChain Chuỗi filter tiếp theo cần gọi
     * @throws ServletException nếu có lỗi servlet
     * @throws IOException      nếu có lỗi I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Đọc header Authorization từ request
        String authHeader = request.getHeader("Authorization");

        // Kiểm tra header có tồn tại và đúng định dạng "Bearer {token}"
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Cắt bỏ 7 ký tự "Bearer " để lấy phần token thuần túy
            String token = authHeader.substring(7);

            // Validate token - kiểm tra chữ ký, thời hạn, tính toàn vẹn
            if (jwtUtils.validateToken(token)) {
                // Trích xuất thông tin người dùng từ payload của token
                String subject = jwtUtils.extractSubject(token); // Email người dùng
                String role = jwtUtils.extractRole(token);       // Vai trò (ADMIN, MANAGER...)

                // Tạo Authentication object với thông tin người dùng và quyền của họ
                // Principal = subject (email), Credentials = null (không cần password nữa),
                // Authorities = danh sách quyền (ở đây chỉ có một role)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject,
                                null,
                                List.of(new SimpleGrantedAuthority(role)) // Gán quyền từ role trong token
                        );

                // Lưu authentication vào SecurityContext để Spring Security nhận biết
                // request này đã được xác thực
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // Nếu token không hợp lệ, SecurityContext không có auth → request bị từ chối sau
        }

        // Chuyển request sang filter tiếp theo trong chain (bất kể có token hay không)
        filterChain.doFilter(request, response);
    }
}
