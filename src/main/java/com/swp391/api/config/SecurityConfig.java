package com.swp391.api.config;

import com.swp391.api.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"message\":\"You do not have permission to perform this action.\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/order-access/**", "/api/home", "/", "/error").permitAll()
                        .requestMatchers("/api/tables/**").permitAll()
                        .requestMatchers(req -> req.getRequestURI().startsWith("/api/qr/")).permitAll()
                        .requestMatchers("/api/payments/sepay/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu/**")
                        .hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/menu/**", "/api/menu-categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/menu-categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/promotions", "/api/promotions/**")
                        .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/promotions", "/api/promotions/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/orders/**").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/payments/**").hasAnyRole("ADMIN", "MANAGER", "RECEPTIONIST")
                        .requestMatchers("/api/check-in/**").hasAnyRole("ADMIN", "MANAGER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/walk-in").hasAnyRole("ADMIN", "MANAGER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/confirm").hasAnyRole("ADMIN", "MANAGER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/assign-tables").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/change-tables").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/cancel").authenticated()
                        .anyRequest().authenticated()
                )
                // kiểm tra Token
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
