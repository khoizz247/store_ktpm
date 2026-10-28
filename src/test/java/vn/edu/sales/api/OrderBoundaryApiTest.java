package vn.edu.sales.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.support.MemberAApiTestSupport;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderBoundaryApiTest extends MemberAApiTestSupport {
    private String admin() throws Exception {
        createUser("boundary-admin@example.test", Role.ADMIN);
        return login("boundary-admin@example.test", ORIGINAL);
    }

    private long product(String admin, String price, int stock) throws Exception {
        var result = mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"BOUNDARY-1","name":"Snapshot name","price":%s,"stockQuantity":%d}
                """.formatted(price, stock))).andExpect(status().isCreated()).andReturn();
        return payload(result).path("id").asLong();
    }

    private String order(long productId, int quantity) {
        return """
                {"recipientName":"Receiver","recipientPhone":"0900000000","shippingAddress":"Test address",
                 "items":[{"productId":%d,"quantity":%d}]}
                """.formatted(productId, quantity);
    }

    @Test
    void nullOrderLineAndMissingQuantityAre400Not500() throws Exception {
        String customer = registerCustomer();
        for (String items : new String[]{"[null]", "[{\"productId\":1}]", "[{\"quantity\":1}]", "[]"}) {
            String request = """
                    {"recipientName":"Receiver","recipientPhone":"0900000000","shippingAddress":"Test address",
                    "items":%s}
                    """.formatted(items);
            var result = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + customer)
                    .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andReturn();
            assertError(result, 400, "VALIDATION_ERROR", "/api/orders");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void stockUpdateResponseContainsPersistedOptimisticVersion() throws Exception {
        String admin = admin();
        long id = product(admin, "10.00", 5);
        var updated = payload(mvc.perform(patch("/api/products/{id}/stock", id)
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockQuantity\":6}")).andExpect(status().isOk()).andReturn());
        var fetched = payload(mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk()).andReturn());
        assertThat(updated.path("version").asLong()).isGreaterThan(0);
        assertThat(updated.path("version")).isEqualTo(fetched.path("version"));
        assertThat(updated.path("stockQuantity").asInt()).isEqualTo(6);
    }

    @Test
    void missingOrNullStockDoesNotSilentlyResetInventory() throws Exception {
        String admin = admin();
        long id = product(admin, "10.00", 5);
        for (String request : new String[]{"{}", "{\"stockQuantity\":null}"}) {
            mvc.perform(patch("/api/products/{id}/stock", id).header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isEqualTo(1);
    }

    @Test
    void pricePrecisionMatchesDatabaseInsteadOfBeingSilentlyRounded() throws Exception {
        String admin = admin();
        for (String price : new String[]{"1.001", "10000000000000"}) {
            mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                    {"sku":"BAD-PRICE","name":"Product","price":%s,"stockQuantity":1}
                    """.formatted(price))).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
    }

    @Test
    void excessiveOrderTotalIsConflictWithoutStockOrAuditChanges() throws Exception {
        String admin = admin();
        long id = product(admin, "9999999999999.99", 2);
        String customer = registerCustomer();
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON).content(order(id, 2))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE movement_type='SALE'", Integer.class)).isZero();
    }

    @Test
    void adminCancellationRecordsActorRefreshesTimeAndRestoresOnlyOnce() throws Exception {
        String admin = admin();
        long id = product(admin, "12.50", 5);
        String customer = registerCustomer();
        var created = payload(mvc.perform(post("/api/orders").header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON).content(order(id, 2)))
                .andExpect(status().isCreated()).andReturn());
        long orderId = created.path("id").asLong();
        // Deterministically make the old timestamp earlier; no timing-dependent sleep assertion.
        jdbc.update("UPDATE orders SET updated_at=? WHERE id=?", LocalDateTime.of(2020, 1, 1, 0, 0), orderId);
        var cancelled = payload(mvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk()).andReturn());
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(LocalDateTime.parse(cancelled.path("updatedAt").asText())).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
        var fetched = payload(mvc.perform(get("/api/admin/orders/{id}", orderId)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn());
        assertThat(fetched.path("updatedAt")).isEqualTo(cancelled.path("updatedAt"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch("/api/admin/orders/{id}/status", orderId).header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                    .andExpect(status().isOk());
        }
        Long actorId = users.findByEmail("boundary-admin@example.test").orElseThrow().id();
        assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM inventory_movements WHERE movement_type='SALE_REVERSAL'", Long.class))
                .isEqualTo(actorId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE movement_type='SALE_REVERSAL'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, id)).isEqualTo(5);
    }

    @Test
    void customerCannotReadCancelOrSearchAnotherCustomersOrder() throws Exception {
        String admin = admin();
        long id = product(admin, "12.50", 5);
        String owner = registerCustomer();
        var created = payload(mvc.perform(post("/api/orders").header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON).content(order(id, 1)))
                .andExpect(status().isCreated()).andReturn());
        createUser("other@example.test", Role.CUSTOMER);
        String other = login("other@example.test", ORIGINAL);
        long orderId = created.path("id").asLong();
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/orders/{id}/cancel", orderId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        var search = payload(mvc.perform(get("/api/orders/me/search").param("code", created.path("orderCode").asText())
                .header("Authorization", "Bearer " + other)).andExpect(status().isOk()).andReturn());
        assertThat(search.path("totalElements").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, id)).isEqualTo(4);
    }

    @Test
    void multibytePasswordOverBcryptLimitReturns400WithoutSavingUser() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(java.util.Map.of(
                "fullName", "Test", "email", "utf8@example.test", "password", "Đ".repeat(40)))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }
}
