package vn.edu.sales.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.edu.sales.api.system.common.ApiError;
import vn.edu.sales.infrastructure.security.JwtAuthenticationFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Mặc định không cho origin nào gọi chéo (chỉ same-origin). Frontend chạy domain khác
     * thì khai báo trong app.cors.allowed-origins, ví dụ: http://localhost:5173,https://shop.example.com
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (origins.contains("*"))
            throw new IllegalStateException("app.cors.allowed-origins không được dùng '*'; hãy liệt kê origin cụ thể");
        if (origins.isEmpty()) return source;
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(origins);
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        api.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        api.setExposedHeaders(List.of("Retry-After"));
        // JWT gửi qua header Authorization, không dùng cookie, nên không cần credentials.
        api.setAllowCredentials(false);
        api.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", api);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper,
                                            @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled)
            throws Exception {
        List<String> publicPaths = new ArrayList<>(List.of("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs/**", "/error", "/api/health"));
        // H2 console chỉ mở khi đang bật (profile H2 local); profile mysql tắt nên đường dẫn này bị chặn.
        if (h2ConsoleEnabled) publicPaths.add("/h2-console/**");
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiError.of(
                                    HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                    "Yêu cầu đăng nhập", request.getRequestURI()));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiError.of(
                                    HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                                    "Không đủ quyền truy cập", request.getRequestURI()));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers("/api/cart/**", "/api/cart").hasRole("CUSTOMER")
                        .requestMatchers("/api/orders/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/products/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
