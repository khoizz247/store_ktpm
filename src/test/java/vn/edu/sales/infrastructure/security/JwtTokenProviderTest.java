package vn.edu.sales.infrastructure.security;

import org.junit.jupiter.api.Test;
import vn.edu.sales.domain.model.Role;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-0123456789-abcdefghij".getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripsSubjectAndTokenVersion() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        var claims = provider.parse(provider.generate("user@example.test", Role.CUSTOMER, 3));
        assertThat(claims.email()).isEqualTo("user@example.test");
        assertThat(claims.tokenVersion()).isEqualTo(3);
    }

    @Test
    void refusesToStartWithMissingInvalidOrShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        for (String secret : new String[]{null, "", "   ", "not base64 !!", shortSecret}) {
            assertThatThrownBy(() -> new JwtTokenProvider(secret, 60_000))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }
}
