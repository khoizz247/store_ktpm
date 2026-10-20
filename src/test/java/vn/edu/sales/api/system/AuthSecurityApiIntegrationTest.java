package vn.edu.sales.api.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.infrastructure.security.JwtTokenProvider;
import vn.edu.sales.support.MemberAApiTestSupport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSecurityApiIntegrationTest extends MemberAApiTestSupport {
    @Test
    void publicHealthProductsAndOpenApiDoNotRequireJwt() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void protectedGetAndPostWithoutJwtReturn401WithApiError() throws Exception {
        var getResult = mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized()).andReturn();
        assertError(getResult, 401, "UNAUTHORIZED", "/api/users/me");
        var postResult = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertError(postResult, 401, "UNAUTHORIZED", "/api/orders");
    }

    @Test
    void malformedAndWrongSignatureTokensCannotReadProtectedEndpoint() throws Exception {
        createUser(EMAIL, Role.CUSTOMER);
        String anotherKey = Base64.getEncoder().encodeToString(
                "other-public-test-key-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));
        String wrongSignature = new JwtTokenProvider(anotherKey, 3600000).generate(EMAIL, Role.CUSTOMER);
        for (String token : List.of("not-a-jwt", wrongSignature)) {
            var result = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andReturn();
            assertError(result, 401, "UNAUTHORIZED", "/api/users/me");
        }
    }

    @Test
    void expiredTokenReturns401WithoutUsingSleep() throws Exception {
        createUser(EMAIL, Role.CUSTOMER);
        String expired = new JwtTokenProvider(testSecret, -120000).generate(EMAIL, Role.CUSTOMER);
        var result = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andReturn();
        assertError(result, 401, "UNAUTHORIZED", "/api/users/me");
    }

    @Test
    void tamperingWithSubjectWithoutResigningTokenReturns401() throws Exception {
        String token = registerCustomer();
        String[] parts = token.split("\\.");
        ObjectNode claims = (ObjectNode) json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        claims.put("sub", "attacker-a@example.test");
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));
        String tampered = parts[0] + "." + payload + "." + parts[2];
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotWriteProductsOrReadAdminRoutes() throws Exception {
        String token = registerCustomer();
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/products"), put("/api/products/999"), delete("/api/products/999"),
                patch("/api/products/999/stock"))) {
            var result = mvc.perform(request.header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andReturn();
            assertError(result, 403, "FORBIDDEN", result.getRequest().getRequestURI());
        }
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products/999/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUpdateChangeStockAndSoftDeleteProduct() throws Exception {
        createUser("admin-a@example.test", Role.ADMIN);
        String token = login("admin-a@example.test", ORIGINAL);
        var created = mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                                "sku", "A-SECURITY-001", "name", "Member A demo", "description", "test only",
                                "price", 120000, "stockQuantity", 10))))
                .andExpect(status().isCreated()).andReturn();
        long productId = payload(created).get("id").asLong();
        mvc.perform(put("/api/products/{id}", productId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Updated", "description", "",
                                "price", 130000, "stockQuantity", 10))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(130000));
        mvc.perform(patch("/api/products/{id}/stock", productId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\":12}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(12));
        mvc.perform(delete("/api/products/{id}", productId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isNotFound());
    }

    @Test
    void adminDoesNotAutomaticallyHaveCustomerPermissions() throws Exception {
        createUser("admin-a@example.test", Role.ADMIN);
        String token = login("admin-a@example.test", ORIGINAL);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cart/checkout").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
