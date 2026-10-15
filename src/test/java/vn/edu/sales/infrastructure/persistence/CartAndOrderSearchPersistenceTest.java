package vn.edu.sales.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import vn.edu.sales.application.port.out.*;
import vn.edu.sales.domain.model.*;
import vn.edu.sales.application.services.OrderService;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.infrastructure.persistence.order.JdbcOrderSearchStore;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:d3-stores;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false"
})
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class CartAndOrderSearchPersistenceTest {
    @Autowired CartStore carts;
    @Autowired OrderSearchStore search;
    @Autowired TransactionRunner transactions;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orderService;
    private Long alice;
    private Long bob;
    private Product keyboard;
    private Product mouse;
    private Long older;
    private Long newer;
    private Long other;

    @BeforeEach
    void prepare() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        alice = user("alice@example.test");
        bob = user("bob@example.test");
        keyboard = products.save(new Product(null, "D3-KB", "Keyboard", "", new BigDecimal("120000"),
                10, ProductStatus.ACTIVE, null));
        mouse = products.save(new Product(null, "D3-MS", "Mouse", "", new BigDecimal("50000"),
                20, ProductStatus.INACTIVE, null));
        older = order(alice, "ORD-ALPHA-1", "PENDING", "2026-10-01 10:00:00");
        newer = order(alice, "ORD-ALPHA-2", "CONFIRMED", "2026-10-02 10:00:00");
        other = order(bob, "ORD-BETA-1", "PENDING", "2026-10-02 10:00:00");
    }

    private Long user(String email) {
        return users.save(new User(null, email, "test-hash", "Store test", Role.CUSTOMER,
                UserStatus.ACTIVE, null)).id();
    }

    private Long order(Long owner, String code, String state, String created) {
        jdbc.update("INSERT INTO orders(order_code,user_id,recipient_name,recipient_phone,shipping_address," +
                        "status,total_amount,created_at,updated_at) VALUES(?,?, 'Test','0900000000','Test',?,0,?,?)",
                code, owner, state, created, created);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, code);
    }

    @Test
    void cartMapsCurrentProductDataAndOrdersLinesByProductId() {
        carts.put(alice, mouse.id(), 1);
        carts.put(alice, keyboard.id(), 2);
        var lines = carts.findByUser(alice);
        assertThat(lines).extracting(CartStore.Line::productId).containsExactly(keyboard.id(), mouse.id());
        var first = lines.getFirst();
        assertThat(first.sku()).isEqualTo("D3-KB");
        assertThat(first.name()).isEqualTo("Keyboard");
        assertThat(first.unitPrice()).isEqualByComparingTo("120000");
        assertThat(first.stockQuantity()).isEqualTo(10);
        assertThat(first.productStatus()).isEqualTo("ACTIVE");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(lines.getLast().productStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void putReplacesQuantityWithoutReservingStockOrTouchingOtherCustomer() {
        carts.put(alice, keyboard.id(), 2);
        carts.put(bob, keyboard.id(), 4);
        carts.put(alice, keyboard.id(), 3);
        assertThat(carts.findByUser(alice)).singleElement().extracting(CartStore.Line::quantity).isEqualTo(3);
        assertThat(carts.findByUser(bob)).singleElement().extracting(CartStore.Line::quantity).isEqualTo(4);
        assertThat(products.findById(keyboard.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isZero();
    }

    @Test
    void removeAndClearAreScopedToOwnerAndAreRepeatable() {
        carts.put(alice, keyboard.id(), 2);
        carts.put(alice, mouse.id(), 1);
        carts.put(bob, keyboard.id(), 4);
        carts.remove(alice, keyboard.id());
        carts.remove(alice, keyboard.id());
        assertThat(carts.findByUser(alice)).singleElement().extracting(CartStore.Line::productId).isEqualTo(mouse.id());
        carts.clear(alice);
        carts.clear(alice);
        assertThat(carts.findByUser(alice)).isEmpty();
        assertThat(carts.findByUser(bob)).hasSize(1);
    }

    @Test
    void lockedReadAndClearParticipateInRollback() {
        carts.put(alice, keyboard.id(), 2);
        assertThatThrownBy(() -> transactions.execute(() -> {
            assertThat(carts.findByUserForUpdate(alice)).hasSize(1);
            carts.clear(alice);
            throw new IllegalStateException("rollback cart");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(carts.findByUser(alice)).hasSize(1);
    }

    @Test
    void databaseRejectsInvalidQuantityAndForeignKeys() {
        assertThatThrownBy(() -> carts.put(alice, keyboard.id(), 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> carts.put(alice, -1L, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> carts.put(-1L, keyboard.id(), 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(carts.findByUser(alice)).isEmpty();
    }

    @Test
    void searchCombinesOwnerStatusAndCodeAndNeverReturnsAnotherOwnersIds() {
        assertThat(search.search(alice, null, "ALPHA", 0, 20).orderIds()).containsExactly(newer, older);
        var filtered = search.search(alice, OrderStatus.PENDING, "ALPHA", 0, 20);
        assertThat(filtered.orderIds()).containsExactly(older);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(search.search(alice, null, "BETA", 0, 20).orderIds()).isEmpty();
        assertThat(search.search(bob, null, null, 0, 20).orderIds()).containsExactly(other);
    }

    @Test
    void adminSearchHasStablePagesAndCountAcrossPages() {
        var first = search.search(null, null, "", 0, 2);
        var second = search.search(null, null, "", 1, 2);
        assertThat(first.orderIds()).containsExactly(other, newer);
        assertThat(second.orderIds()).containsExactly(older);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(search.search(null, null, "", 20, 2).orderIds()).isEmpty();
    }

    @Test
    void codeIsBoundAsDataAndNoMatchesHaveZeroCount() {
        var result = search.search(alice, null, "' OR 1=1 --", 0, 20);
        assertThat(result.orderIds()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void invalidPaginationIsRejectedBeforeQuerying() {
        // No JdbcTemplate: invalid input must fail before any database access.
        var directStore = new JdbcOrderSearchStore(null);
        for (int[] pagination : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> directStore.search(null, null, "", pagination[0], pagination[1]))
                    .isInstanceOf(IllegalArgumentException.class);
            // The injected @Repository bean translates the original exception via Spring's proxy.
            assertThatThrownBy(() -> search.search(null, null, "", pagination[0], pagination[1]))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void integratedC3CreatesSearchableOrderAndCommitsStockAndAudit() {
        var created = orderService.create("alice@example.test", "Alice", "0900000000", "Test",
                List.of(new OrderService.CreateLine(keyboard.id(), 2)));
        assertThat(created.id()).isNotNull();
        assertThat(created.totalAmount()).isEqualByComparingTo("240000");
        assertThat(created.items()).hasSize(1);
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(products.findById(keyboard.id()).orElseThrow().stockQuantity()).isEqualTo(8);
        assertThat(search.search(alice, OrderStatus.PENDING, created.orderCode(), 0, 20).orderIds())
                .containsExactly(created.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE order_id=? AND quantity_change=-2",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void integratedC3RejectsInsufficientStockWithoutPersistingPartialData() {
        assertThatThrownBy(() -> orderService.create("alice@example.test", "Alice", "0900000000", "Test",
                List.of(new OrderService.CreateLine(keyboard.id(), 11))))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(products.findById(keyboard.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(search.search(null, null, "", 0, 20).totalElements()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isZero();
    }
}
