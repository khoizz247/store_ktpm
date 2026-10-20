package vn.edu.sales.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.UserStatus;
import vn.edu.sales.infrastructure.persistence.product.SpringDataProductRepository;
import vn.edu.sales.infrastructure.persistence.user.SpringDataUserRepository;
import vn.edu.sales.infrastructure.persistence.user.UserJpaEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:inventory_movements.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SingleStoreApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SpringDataUserRepository users;

    @Autowired
    SpringDataProductRepository products;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM inventory_movements");
        products.deleteAll();
        users.deleteAll();
    }

    @Test
    void oneShopFlowEnforcesRolesStockAndCancellation() throws Exception {
        users.save(new UserJpaEntity(
                null,
                "owner@shop.test",
                passwords.encode("Owner@123456"),
                "Chủ cửa hàng",
                Role.ADMIN,
                UserStatus.ACTIVE));

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        String customerToken = token(
                mvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "fullName":"Khách hàng",
                                            "email":"customer@shop.test",
                                            "password":"Customer@123"
                                        }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sku":"ONE-001",
                                    "name":"Hàng mẫu",
                                    "description":"",
                                    "price":100000,
                                    "stockQuantity":2
                                }
                                """))
                .andExpect(status().isForbidden());

        String adminToken = token(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email":"owner@shop.test",
                                    "password":"Owner@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode product = json.readTree(mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sku":"ONE-001",
                                    "name":"Hàng mẫu",
                                    "description":"",
                                    "price":100000,
                                    "stockQuantity":2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long productId = product.get("id").asLong();

        String orderBody = """
                {
                    "recipientName":"Người nhận",
                    "recipientPhone":"0901234567",
                 "shippingAddress":"Số 1 Hà Nội",
                 "items":[
                    {
                        "productId":%d,
                        "quantity":2
                    }
                 ]}
                """.formatted(productId);

        JsonNode order = json.readTree(mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("200000");
        assertStock(productId, 0);

        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/orders/{id}/cancel", order.get("id").asLong())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        assertStock(productId, 2);

        mvc.perform(patch("/api/orders/{id}/cancel", order.get("id").asLong())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict());
        assertStock(productId, 2);
    }

    @Test
    void concurrentPurchase_enforcesStockAndLocking() throws Exception {
        users.save(new UserJpaEntity(
                null,
                "owner@shop.test",
                passwords.encode("Owner@123456"),
                "Chủ cửa hàng",
                Role.ADMIN,
                UserStatus.ACTIVE
        ));

        String adminToken = token(
                mvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email":"owner@shop.test",
                                            "password":"Owner@123456"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        String customer1Token = token(
                mvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "fullName":"Khách hàng 1",
                                            "email":"customer1@shop.test",
                                            "password":"Customer@123"
                                        }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        String customer2Token = token(
                mvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "fullName":"Khách hàng 2",
                                            "email":"customer2@shop.test",
                                            "password":"Customer@123"
                                        }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        JsonNode product = json.readTree(
                mvc.perform(post("/api/products")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "sku":"CONC-001",
                                            "name":"Sản phẩm tranh chấp",
                                            "description":"",
                                            "price":50000,
                                            "stockQuantity":1
                                        }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        long productId = product.get("id").asLong();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        String orderBody = """
                {
                    "recipientName":"Người nhận",
                    "recipientPhone":"0901234567",
                    "shippingAddress":"Số 1 Hà Nội",
                    "items":[
                        {
                            "productId":%d,
                            "quantity":1
                        }
                    ]
                }
                """.formatted(productId);

        String[] customerTokens = {customer1Token, customer2Token};

        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String tokenForThisThread = customerTokens[i];
            futures.add(executor.submit(() -> {
                startLatch.await();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(tokenForThisThread);
                HttpEntity<String> entity = new HttpEntity<>(orderBody, headers);
                return restTemplate.postForEntity("/api/orders", entity, String.class);
            }));
        }

        startLatch.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get();
            if (response.getStatusCode().is2xxSuccessful()) {
                successCount++;
            } else if (response.getStatusCode().value() == 409) {
                conflictCount++;
            }
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(terminated).isTrue();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
        assertStock(productId, 0);
        Long saleAuditCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM inventory_movements
                        WHERE movement_type = ?
                        """,
                Long.class,
                "SALE"
        );

        assertThat(saleAuditCount).isEqualTo(1);
    }

    private String token(String response) throws Exception {
        return json.readTree(response).get("accessToken").asText();
    }

    private void assertStock(long id, int expected) throws Exception {
        JsonNode product = json.readTree(mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(product.get("stockQuantity").asInt()).isEqualTo(expected);
    }
}