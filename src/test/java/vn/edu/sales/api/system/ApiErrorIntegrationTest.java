package vn.edu.sales.api.system;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.edu.sales.support.MemberAApiTestSupport;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorIntegrationTest extends MemberAApiTestSupport {
    @Test
    void invalidRegisterReturns400WithFieldsInsteadOfFieldErrors() throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"invalid\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertError(result, 400, "VALIDATION_ERROR", "/api/auth/register");
        assertThat(payload(result).path("fields").has("fullName")).isTrue();
        assertThat(payload(result).path("fields").has("email")).isTrue();
        assertThat(payload(result).path("fields").has("password")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void malformedJsonAndMissingBodyReturn400() throws Exception {
        for (String value : new String[]{"{", ""}) {
            var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(value))
                    .andExpect(status().isBadRequest()).andReturn();
            assertError(result, 400, "INVALID_REQUEST", "/api/auth/login");
        }
    }

    @Test
    void wrongLoginReturns401WithoutDisclosingWhetherAccountExists() throws Exception {
        registerCustomer();
        String firstMessage = null;
        for (String email : new String[]{EMAIL, "missing-a@example.test"}) {
            var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", email, "password", "WrongPassword@123"))))
                    .andExpect(status().isUnauthorized()).andReturn();
            assertError(result, 401, "INVALID_CREDENTIALS", "/api/auth/login");
            String message = payload(result).path("message").asText();
            if (firstMessage == null) firstMessage = message;
            else assertThat(message).isEqualTo(firstMessage);
        }
    }

    @Test
    void duplicateRegistrationReturns409AndDoesNotCreateSecondUser() throws Exception {
        registerCustomer();
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Duplicate", "email", EMAIL.toUpperCase(java.util.Locale.ROOT),
                                "password", ORIGINAL))))
                .andExpect(status().isConflict()).andReturn();
        assertError(result, 409, "BUSINESS_CONFLICT", "/api/auth/register");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test
    void missingResourceAndInvalidParameterUseSameErrorEnvelope() throws Exception {
        var notFound = mvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound()).andReturn();
        assertError(notFound, 404, "RESOURCE_NOT_FOUND", "/api/products/999999");
        var badType = mvc.perform(get("/api/products/search").param("size", "not-a-number"))
                .andExpect(status().isBadRequest()).andReturn();
        assertError(badType, 400, "INVALID_REQUEST", "/api/products/search");
    }
}
