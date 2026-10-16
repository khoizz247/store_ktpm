package vn.edu.sales.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.sales.application.port.out.*;
import vn.edu.sales.domain.model.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:d5-cart-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false"
})
@AutoConfigureMockMvc
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CartApiIntegrationTest {
    private static final String CHECKOUT = """
            {"recipientName":"Alice","recipientPhone":"0900000000","shippingAddress":"Demo address"}
            """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired TokenProvider tokens;
    private Product product;
    private Long aliceId;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void prepare() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        aliceId = user("alice@example.test", Role.CUSTOMER);
        user("bob@example.test", Role.CUSTOMER);
        user("admin@example.test", Role.ADMIN);
        alice = "Bearer " + tokens.generate("alice@example.test", Role.CUSTOMER);
        bob = "Bearer " + tokens.generate("bob@example.test", Role.CUSTOMER);
        admin = "Bearer " + tokens.generate("admin@example.test", Role.ADMIN);
        product = products.save(new Product(null, "D5-KB", "Keyboard", "", new BigDecimal("120000"),
                10, ProductStatus.ACTIVE, null));
    }

    private Long user(String email, Role role) {
        return users.save(new User(null, email, "test-only-hash", "API test", role, UserStatus.ACTIVE, null)).id();
    }

    private void putQuantity(String token, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", product.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode checkout() throws Exception {
        var result = mvc.perform(post("/api/cart/checkout").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void missingJwtCannotReadOrCheckoutCart() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cart/checkout").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotUseCustomerCartAndCustomerCannotCreateProducts() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/cart/checkout").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)).andExpect(status().isForbidden());
        mvc.perform(post("/api/products").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void quantityIsAbsoluteAndCartDoesNotReserveStock() throws Exception {
        putQuantity(alice, 2);
        putQuantity(alice, 3);
        mvc.perform(get("/api/cart").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(3));
        mvc.perform(get("/api/cart").header("Authorization", bob))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isZero();
    }

    @Test
    void deleteAndClearDoNotAffectOtherCustomers() throws Exception {
        putQuantity(alice, 2);
        putQuantity(bob, 1);
        mvc.perform(delete("/api/cart/items/{id}", product.id()).header("Authorization", alice))
                .andExpect(status().isNoContent());
        putQuantity(alice, 2);
        mvc.perform(delete("/api/cart").header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/cart").header("Authorization", bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(1));
    }

    @Test
    void checkoutCreatesOrderDecreasesStockAndClearsCart() throws Exception {
        putQuantity(alice, 2);
        var order = checkout();
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("userId").asLong()).isEqualTo(aliceId);
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("240000");
        assertThat(order.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(8);
        mvc.perform(get("/api/cart").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE order_id=? AND movement_type='SALE' AND quantity_change=-2 AND created_by_user_id=?",
                Integer.class, order.get("id").asLong(), aliceId)).isEqualTo(1);
    }

    @Test
    void invalidQuantityAndRecipientReturn400WithoutMutatingCart() throws Exception {
        for (String body : List.of("{}", "{\"quantity\":null}", "{\"quantity\":0}")) {
            mvc.perform(put("/api/cart/items/{id}", product.id()).header("Authorization", alice)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        putQuantity(alice, 2);
        mvc.perform(post("/api/cart/checkout").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cart").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(2));
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
    }

    @Test
    void emptyCheckoutReturns409AndCreatesNoOrder() throws Exception {
        mvc.perform(post("/api/cart/checkout").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
    }

    @Test
    void depletedStockAtCheckoutKeepsCartAndCreatesNoPartialData() throws Exception {
        putQuantity(alice, 2);
        jdbc.update("UPDATE products SET stock_quantity=1 WHERE id=?", product.id());
        mvc.perform(post("/api/cart/checkout").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)).andExpect(status().isConflict());
        mvc.perform(get("/api/cart").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(2));
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isZero();
    }

    @Test
    void checkoutOrderCanBeCancelledOnceThroughC5Api() throws Exception {
        putQuantity(alice, 2);
        long orderId = checkout().get("id").asLong();
        mvc.perform(patch("/api/orders/{id}/cancel", orderId).header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        mvc.perform(patch("/api/orders/{id}/cancel", orderId).header("Authorization", alice))
                .andExpect(status().isConflict());
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE order_id=? AND movement_type='SALE_REVERSAL'",
                Integer.class, orderId)).isEqualTo(1);
    }
}
