package com.swp391.api.config;

import com.swp391.api.common.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/order-access/**", "/").permitAll()
                        .requestMatchers("/api/tables/**").permitAll()
                        .requestMatchers(req -> req.getRequestURI().startsWith("/api/qr/")).permitAll()
                        .requestMatchers("/api/payments/sepay/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu/**", "/api/menu-categories/**")
                        .hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/menu/**").hasAnyRole("ADMIN", "MANAGER", "WAITER")
                        .requestMatchers("/api/menu-categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/promotions/**")
                        .hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/promotions/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/orders/**").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/payments/**").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/promotions/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/check-in/**").hasAnyRole("ADMIN", "MANAGER", "WAITER", "RECEPTIONIST")
                        .requestMatchers("/api/reservations/**").permitAll()
                        .anyRequest().authenticated()
                )
                // kiểm tra Token
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
