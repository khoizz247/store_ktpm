package vn.edu.sales.api.system;

import org.junit.jupiter.api.Test;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.support.MemberAApiTestSupport;

import org.springframework.http.MediaType;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountApiIntegrationTest extends MemberAApiTestSupport {
    @Test
    void meReturnsOnlyCurrentIdentityWithoutPasswordOrHash() throws Exception {
        String token = registerCustomer();
        var result = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER")).andReturn();
        assertThat(payload(result).has("password")).isFalse();
        assertThat(payload(result).has("passwordHash")).isFalse();
        assertThat(payload(result).path("id").asLong()).isEqualTo(users.findByEmail(EMAIL).orElseThrow().id());
    }

    @Test
    void changingPasswordPersistsHashAndOnlyNewPasswordCanLogIn() throws Exception {
        String token = registerCustomer();
        String previousHash = hashOf(EMAIL);
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", REPLACEMENT))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        String currentHash = hashOf(EMAIL);
        assertThat(currentHash).isNotEqualTo(previousHash).isNotEqualTo(REPLACEMENT);
        assertThat(passwords.matches(REPLACEMENT, currentHash)).isTrue();
        assertThat(passwords.matches(ORIGINAL, currentHash)).isFalse();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", EMAIL, "password", ORIGINAL))))
                .andExpect(status().isUnauthorized());
        String newToken = login(EMAIL, REPLACEMENT);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void wrongCurrentPasswordLeavesStoredHashUnchanged() throws Exception {
        String token = registerCustomer();
        String before = hashOf(EMAIL);
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "WrongPassword@123", "newPassword", REPLACEMENT))))
                .andExpect(status().isUnauthorized());
        assertThat(hashOf(EMAIL)).isEqualTo(before);
        login(EMAIL, ORIGINAL);
    }

    @Test
    void weakAndSamePasswordsReturn400WithoutChangingHash() throws Exception {
        String token = registerCustomer();
        String before = hashOf(EMAIL);
        for (String candidate : new String[]{"short", "alllowercase123!", ORIGINAL, "Aa1!" + "é".repeat(35)}) {
            mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", candidate))))
                    .andExpect(status().isBadRequest());
            assertThat(hashOf(EMAIL)).isEqualTo(before);
        }
    }

    @Test
    void missingPasswordFieldsReturn400WithoutChangingHash() throws Exception {
        String token = registerCustomer();
        String before = hashOf(EMAIL);
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(hashOf(EMAIL)).isEqualTo(before);
    }

    @Test
    void accountEndpointsRequireJwt() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", REPLACEMENT))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingOwnPasswordCannotChangeAnotherAccount() throws Exception {
        String token = registerCustomer();
        createUser("other-a@example.test", Role.CUSTOMER);
        String otherHash = hashOf("other-a@example.test");
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", REPLACEMENT))))
                .andExpect(status().isNoContent());
        assertThat(hashOf("other-a@example.test")).isEqualTo(otherHash);
        login("other-a@example.test", ORIGINAL);
    }

    @Test
    void lockedAccountCannotChangePasswordUsingPreviouslyIssuedToken() throws Exception {
        String token = registerCustomer();
        String before = hashOf(EMAIL);
        jdbc.update("UPDATE users SET status='LOCKED' WHERE email=?", EMAIL);
        mvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ORIGINAL, "newPassword", REPLACEMENT))))
                .andExpect(status().isUnauthorized());
        assertThat(hashOf(EMAIL)).isEqualTo(before);
    }
}
