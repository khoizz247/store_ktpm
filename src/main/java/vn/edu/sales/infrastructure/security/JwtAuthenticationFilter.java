package vn.edu.sales.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Xác thực JWT rồi đối chiếu với DB ở mỗi request, để tài khoản bị khóa, bị đổi role
 * hoặc vừa đổi mật khẩu không thể tiếp tục dùng token cũ cho tới khi token hết hạn.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository users) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            Optional<User> user = authenticate(authorization.substring(7));
            if (user.isPresent()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user.get().email(),
                        null,
                        // Role lấy từ DB, không tin role ghi trong token.
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> authenticate(String token) {
        JwtTokenProvider.TokenClaims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (RuntimeException invalidToken) {
            return Optional.empty();
        }
        return users.findByEmail(claims.email().toLowerCase(Locale.ROOT))
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .filter(user -> user.tokenVersion() == claims.tokenVersion());
    }
}
