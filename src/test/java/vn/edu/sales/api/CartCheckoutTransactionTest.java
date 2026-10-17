package vn.edu.sales.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.sales.application.port.out.*;
import vn.edu.sales.application.services.CartService;
import vn.edu.sales.domain.model.*;
import vn.edu.sales.infrastructure.persistence.order.JdbcCartStore;
import vn.edu.sales.infrastructure.persistence.product.JdbcInventoryStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:d6-checkout;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=15000",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false"
})
@AutoConfigureMockMvc
@Import(CartCheckoutTransactionTest.ProbeConfig.class)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CartCheckoutTransactionTest {
    private static final String CHECKOUT = """
            {"recipientName":"D6 customer","recipientPhone":"0900000000","shippingAddress":"Demo address"}
            """;
    private static final String ALICE = "alice-d6@example.test";
    private static final String BOB = "bob-d6@example.test";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired TokenProvider tokens;
    @Autowired CartService service;
    @Autowired FaultProbe probe;
    private Product first;
    private Product second;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void prepare() {
        probe.reset();
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        aliceId = customer(ALICE);
        bobId = customer(BOB);
        first = product("D6-FIRST");
        second = product("D6-SECOND");
    }

    private Long customer(String email) {
        return users.save(new User(null, email, "test-only-hash", "D6", Role.CUSTOMER, UserStatus.ACTIVE, null)).id();
    }

    private Product product(String sku) {
        return products.save(new Product(null, sku, sku, "", new BigDecimal("120000"),
                10, ProductStatus.ACTIVE, null));
    }

    private String token(String email) { return "Bearer " + tokens.generate(email, Role.CUSTOMER); }

    private int checkoutStatus(String email) throws Exception {
        return mvc.perform(post("/api/cart/checkout").header("Authorization", token(email))
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andReturn().getResponse().getStatus();
    }

    private void checkoutDirect() {
        service.checkout(ALICE, "D6", "0900000000", "Demo address");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private int stock(Long id) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, id);
    }

    private void noOrderOrAudit() {
        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
        assertThat(count("inventory_movements")).isZero();
    }

    @Test
    void productMadeInactiveAfterAddingCartRejectsCheckoutAndKeepsEveryLine() throws Exception {
        service.put(ALICE, first.id(), 2);
        service.put(ALICE, second.id(), 1);
        jdbc.update("UPDATE products SET status='INACTIVE' WHERE id=?", second.id());

        mvc.perform(post("/api/cart/checkout").header("Authorization", token(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)).andExpect(status().isNotFound());

        noOrderOrAudit();
        assertThat(stock(first.id())).isEqualTo(10);
        assertThat(stock(second.id())).isEqualTo(10);
        assertThat(service.mine(ALICE)).extracting(CartStore.Line::quantity).containsExactly(2, 1);
    }

    @Test
    void lastProductLackingStockDoesNotPartiallyPurchaseEarlierProduct() throws Exception {
        service.put(ALICE, first.id(), 2);
        service.put(ALICE, second.id(), 2);
        jdbc.update("UPDATE products SET stock_quantity=1 WHERE id=?", second.id());

        assertThat(checkoutStatus(ALICE)).isEqualTo(409);

        noOrderOrAudit();
        assertThat(stock(first.id())).isEqualTo(10);
        assertThat(stock(second.id())).isEqualTo(1);
        assertThat(service.mine(ALICE)).extracting(CartStore.Line::quantity).containsExactly(2, 2);
    }

    @Test
    void failureAfterFirstRealAuditInsertRollsBackJpaAndJdbcWritesForWholeCart() {
        service.put(ALICE, first.id(), 2);
        service.put(ALICE, second.id(), 1);
        probe.failAfterAudit = true;

        assertThatThrownBy(this::checkoutDirect).isInstanceOf(IllegalStateException.class)
                .hasMessage("D6 failure after audit insert");

        // The failure is injected AFTER real order/stock/audit writes, not before the transaction.
        assertThat(probe.observedOrders).isEqualTo(1);
        assertThat(probe.observedAudits).isEqualTo(1);
        assertThat(probe.observedStock).isEqualTo(8);
        noOrderOrAudit();
        assertThat(stock(first.id())).isEqualTo(10);
        assertThat(stock(second.id())).isEqualTo(10);
        assertThat(service.mine(ALICE)).extracting(CartStore.Line::quantity).containsExactly(2, 1);
    }

    @Test
    void failureAfterRealCartDeleteRestoresCartOrderStockAndAuditTogether() {
        service.put(ALICE, first.id(), 2);
        probe.failAfterClear = true;

        assertThatThrownBy(this::checkoutDirect).isInstanceOf(IllegalStateException.class)
                .hasMessage("D6 failure after cart delete");

        assertThat(probe.observedOrders).isEqualTo(1);
        assertThat(probe.observedAudits).isEqualTo(1);
        assertThat(probe.observedCartSize).isZero();
        noOrderOrAudit();
        assertThat(stock(first.id())).isEqualTo(10);
        assertThat(service.mine(ALICE)).extracting(CartStore.Line::quantity).containsExactly(2);
    }

    @RepeatedTest(3)
    void competingCheckoutsOfSameCartCreateOnlyOneOrderAndOneStockDelta() throws Exception {
        service.put(ALICE, first.id(), 2);

        assertThat(compete(ALICE, ALICE)).containsExactlyInAnyOrder(201, 409);

        assertThat(stock(first.id())).isEqualTo(8);
        assertThat(service.mine(ALICE)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT user_id FROM orders", Long.class)).isEqualTo(aliceId);
        assertOneOrderAndSale();
    }

    @Test
    void twoBuyersCannotBothCheckoutMoreThanRemainingStock() throws Exception {
        service.put(ALICE, first.id(), 2);
        service.put(BOB, first.id(), 2);
        jdbc.update("UPDATE products SET stock_quantity=3 WHERE id=?", first.id());

        assertThat(compete(ALICE, BOB)).containsExactlyInAnyOrder(201, 409);

        assertThat(stock(first.id())).isEqualTo(1);
        Long winner = jdbc.queryForObject("SELECT user_id FROM orders", Long.class);
        assertThat(winner).isIn(aliceId, bobId);
        String winningEmail = winner.equals(aliceId) ? ALICE : BOB;
        String losingEmail = winner.equals(aliceId) ? BOB : ALICE;
        assertThat(service.mine(winningEmail)).isEmpty();
        assertThat(service.mine(losingEmail)).extracting(CartStore.Line::quantity).containsExactly(2);
        assertOneOrderAndSale();
    }

    private void assertOneOrderAndSale() {
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_items")).isEqualTo(1);
        assertThat(count("inventory_movements")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE movement_type='SALE' AND quantity_change=-2 AND stock_after=stock_before+quantity_change",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE stock_quantity<0", Integer.class)).isZero();
    }

    private List<Integer> compete(String one, String two) throws Exception {
        probe.armRace();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Future<Integer> a = executor.submit(() -> { start.await(); return checkoutStatus(one); });
            Future<Integer> b = executor.submit(() -> { start.await(); return checkoutStatus(two); });
            start.countDown();
            assertThat(probe.auditReached.await(15, TimeUnit.SECONDS)).as("first checkout wrote audit").isTrue();
            assertThat(probe.contenders.await(15, TimeUnit.SECONDS)).as("both checkouts entered cart locking").isTrue();
            probe.release.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            probe.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean @Primary
        FaultProbe faultProbe(JdbcCartStore carts, JdbcInventoryStore inventory, JdbcTemplate jdbc) {
            return new FaultProbe(carts, inventory, jdbc);
        }
    }

    // Test-only decorators: delegate all SQL to real stores; production code is unchanged.
    static class FaultProbe implements CartStore, InventoryStore {
        final JdbcCartStore carts;
        final JdbcInventoryStore inventory;
        final JdbcTemplate jdbc;
        volatile boolean failAfterAudit;
        volatile boolean failAfterClear;
        volatile int observedOrders, observedAudits, observedStock, observedCartSize;
        volatile CountDownLatch contenders, auditReached, release;

        FaultProbe(JdbcCartStore carts, JdbcInventoryStore inventory, JdbcTemplate jdbc) {
            this.carts = carts; this.inventory = inventory; this.jdbc = jdbc;
        }

        void reset() {
            failAfterAudit = false; failAfterClear = false;
            observedOrders = -1; observedAudits = -1; observedStock = -1; observedCartSize = -1;
            contenders = null; auditReached = null; release = null;
        }

        void armRace() {
            contenders = new CountDownLatch(2); auditReached = new CountDownLatch(1); release = new CountDownLatch(1);
        }

        @Override public List<Line> findByUser(Long userId) { return carts.findByUser(userId); }
        @Override public List<Line> findByUserForUpdate(Long userId) {
            if (contenders != null) contenders.countDown();
            return carts.findByUserForUpdate(userId);
        }
        @Override public void put(Long userId, Long productId, int quantity) { carts.put(userId, productId, quantity); }
        @Override public void remove(Long userId, Long productId) { carts.remove(userId, productId); }
        @Override public void clear(Long userId) {
            carts.clear(userId);
            if (failAfterClear) {
                observe();
                observedCartSize = jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id=?", Integer.class, userId);
                throw new IllegalStateException("D6 failure after cart delete");
            }
        }
        @Override public List<Movement> byProduct(Long productId) { return inventory.byProduct(productId); }
        @Override public void record(Long productId, Long orderId, String type, int change, int before,
                                     int after, String reference, String note, Long actor) {
            inventory.record(productId, orderId, type, change, before, after, reference, note, actor);
            if (failAfterAudit) {
                observe();
                observedStock = jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?", Integer.class, productId);
                throw new IllegalStateException("D6 failure after audit insert");
            }
            if (auditReached != null) {
                auditReached.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("D6 race gate timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("D6 race interrupted", exception);
                }
            }
        }
        private void observe() {
            observedOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
            observedAudits = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class);
        }
    }
}
