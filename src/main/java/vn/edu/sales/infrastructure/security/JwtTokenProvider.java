package vn.edu.sales.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.domain.model.Role;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider implements TokenProvider {
    static final String TOKEN_VERSION_CLAIM = "tv";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String base64Secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(decodeSecret(base64Secret));
        this.expirationMs = expirationMs;
    }

    private static byte[] decodeSecret(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("Thiếu app.jwt.secret (JWT_SECRET)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET phải là chuỗi Base64", exception);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET phải có ít nhất " + MIN_SECRET_BYTES + " byte ngẫu nhiên");
        }
        return bytes;
    }

    @Override
    public String generate(String email, Role role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Chỉ xác minh chữ ký và hạn dùng. Role và trạng thái tài khoản phải được kiểm tra lại từ DB.
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        if (claims.getSubject() == null || tokenVersion == null) {
            throw new JwtException("Token thiếu thông tin bắt buộc");
        }
        return new TokenClaims(claims.getSubject(), tokenVersion);
    }

    public record TokenClaims(String email, int tokenVersion) {
    }
}
