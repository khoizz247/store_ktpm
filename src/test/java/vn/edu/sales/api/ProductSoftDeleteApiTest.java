package vn.edu.sales.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:b7-softdelete-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false"
})
@AutoConfigureMockMvc
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProductSoftDeleteApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Autowired
    private TokenProvider tokens;

    private String adminToken;
    private String customerToken;
    private Product product;

    @BeforeEach
    void prepareData() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }

        users.save(new User(null, "admin@example.test", "hash", "Admin", Role.ADMIN, UserStatus.ACTIVE, null));
        users.save(new User(null, "customer@example.test", "hash", "Customer", Role.CUSTOMER, UserStatus.ACTIVE, null));

        adminToken = "Bearer " + tokens.generate("admin@example.test", Role.ADMIN);
        customerToken = "Bearer " + tokens.generate("customer@example.test", Role.CUSTOMER);

        product = products.save(new Product(null, "P-SOFT-01", "Bàn phím cơ DareU EK87",
                "Mô tả bàn phím", new BigDecimal("650000"), 10, ProductStatus.ACTIVE, null));
    }

    @Test
    void adminCanSoftDeleteProductAndPublicGetReturns404NotFound() throws Exception {
        mvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.id()))
                .andExpect(jsonPath("$.sku").value("P-SOFT-01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stockQuantity").value(10));

        mvc.perform(delete("/api/products/{id}", product.id())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(get("/api/products/search").param("q", "DareU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        String dbStatus = jdbc.queryForObject("SELECT status FROM products WHERE id=?", String.class, product.id());
        Integer dbStock = jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, product.id());
        assertThat(dbStatus).isEqualTo("INACTIVE");
        assertThat(dbStock).isEqualTo(10);
    }

    @Test
    void deleteProductRequiresAdminRole() throws Exception {
        mvc.perform(delete("/api/products/{id}", product.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(delete("/api/products/{id}", product.id())
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteNonExistentProductReturns404NotFound() throws Exception {
        mvc.perform(delete("/api/products/{id}", 999999L)
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
