package vn.edu.sales.api.system;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.support.MemberAApiTestSupport;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TokenRevocationAndLoginThrottleApiTest extends MemberAApiTestSupport {
    @Test
    void changingPasswordRevokesPreviouslyIssuedTokens() throws Exception {
        String oldToken = registerCustomer();
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", REPLACEMENT))))
                .andExpect(status().isNoContent());

        var result = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized()).andReturn();
        assertError(result, 401, "UNAUTHORIZED", "/api/users/me");

        String newToken = login(EMAIL, REPLACEMENT);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void lockedAccountTokenStopsWorkingImmediately() throws Exception {
        String token = registerCustomer();
        jdbc.update("UPDATE users SET status='LOCKED' WHERE email=?", EMAIL);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleIsReadFromDatabaseNotFromToken() throws Exception {
        String adminEmail = "admin-a@example.test";
        createUser(adminEmail, Role.ADMIN);
        String adminToken = login(adminEmail, ORIGINAL);
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        jdbc.update("UPDATE users SET role='CUSTOMER' WHERE email=?", adminEmail);
        var result = mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden()).andReturn();
        assertError(result, 403, "FORBIDDEN", "/api/admin/orders");
    }

    @Test
    void validlySignedTokenWithoutTokenVersionIsRejected() throws Exception {
        createUser(EMAIL, Role.CUSTOMER);
        Instant now = Instant.now();
        String legacy = Jwts.builder().subject(EMAIL).claim("role", "CUSTOMER")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(testSecret))).compact();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForDeletedAccountIsRejected() throws Exception {
        String token = registerCustomer();
        jdbc.update("DELETE FROM users WHERE email=?", EMAIL);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongPasswordsReturn429WithRetryAfter() throws Exception {
        String email = "throttle-a@example.test";
        createUser(email, Role.CUSTOMER);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", email, "password", "WrongPassword@123"))))
                    .andExpect(status().isUnauthorized());
        }
        // Đúng mật khẩu cũng bị chặn trong thời gian khóa.
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", ORIGINAL))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER)).andReturn();
        assertError(result, 429, "TOO_MANY_ATTEMPTS", "/api/auth/login");
        assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
    }

    @Test
    void registerRejectsWeakPasswordWithoutCreatingUser() throws Exception {
        for (String weak : new String[]{"Short@1", "alllowercase123!", "NoSpecialChar123"}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("fullName", "Weak", "email", "weak-a@example.test", "password", weak))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.findByEmail("weak-a@example.test")).isEmpty();
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejectedByDefault() throws Exception {
        mvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
