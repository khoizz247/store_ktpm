package vn.edu.sales.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:d2-inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
// Real beans only; no Mockito listeners or agent are needed for this integration test.
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class InventoryPersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryStore inventory;
    @Autowired TransactionRunner transactions;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    private Product product;
    private User actor;

    @BeforeEach
    void prepareData() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        actor = users.save(new User(null, "d2@example.test", "test-only-hash", "D2 test",
                Role.ADMIN, UserStatus.ACTIVE, null));
        product = products.save(new Product(null, "D2-001", "D2 product", "",
                new BigDecimal("120000"), 10, ProductStatus.ACTIVE, null));
    }

    @Test
    void initializesAllSixTablesAfterJpaAndWiresImplementations() {
        for (String table : List.of("users", "products", "orders", "order_items", "cart_items", "inventory_movements")) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Integer.class, table)).isEqualTo(1);
        }
        assertThat(transactions).isInstanceOf(SpringTransactionRunner.class);
        assertThat(inventory).isInstanceOf(vn.edu.sales.infrastructure.persistence.product.JdbcInventoryStore.class);
    }

    @Test
    void commitsJpaStockAndJdbcAuditTogetherAndMapsActor() {
        transactions.execute(() -> {
            products.save(product.withStock(8));
            entityManager.flush();
            inventory.record(product.id(), null, "SALE", -2, 10, 8, "D2-COMMIT", "test", actor.id());
            return null;
        });
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(8);
        var movements = inventory.byProduct(product.id());
        assertThat(movements).hasSize(1);
        var movement = movements.getFirst();
        assertThat(movement.actorUserId()).isEqualTo(actor.id());
        assertThat(movement.orderId()).isNull();
        assertThat(movement.type()).isEqualTo("SALE");
        assertThat(movement.quantityChange()).isEqualTo(-2);
        assertThat(movement.stockBefore()).isEqualTo(10);
        assertThat(movement.stockAfter()).isEqualTo(8);
        assertThat(movement.referenceCode()).isEqualTo("D2-COMMIT");
        assertThat(movement.note()).isEqualTo("test");
        assertThat(movement.createdAt()).isNotNull();
    }

    @Test
    void runtimeFailureRollsBackBothJpaStockAndJdbcAudit() {
        assertThatThrownBy(() -> transactions.execute(() -> {
            products.save(product.withStock(8));
            entityManager.flush();
            inventory.record(product.id(), null, "SALE", -2, 10, 8, "D2-ROLLBACK", null, actor.id());
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("simulated failure");
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(inventory.byProduct(product.id())).isEmpty();
    }

    @Test
    void rejectsInconsistentStockArithmeticAndRollsBackProductChange() {
        assertThatThrownBy(() -> transactions.execute(() -> {
            products.save(product.withStock(8));
            entityManager.flush();
            inventory.record(product.id(), null, "SALE", -2, 10, 9, "D2-BAD", null, actor.id());
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(products.findById(product.id()).orElseThrow().stockQuantity()).isEqualTo(10);
        assertThat(inventory.byProduct(product.id())).isEmpty();
    }

    @Test
    void rejectsZeroChangeNegativeStockMissingForeignKeysAndDuplicateReference() {
        assertThatThrownBy(() -> inventory.record(product.id(), null, "SALE", 0, 10, 10,
                "ZERO", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inventory.record(product.id(), null, "SALE", -11, 10, -1,
                "NEGATIVE", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inventory.record(-1L, null, "SALE", -1, 10, 9,
                "BAD-PRODUCT", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inventory.record(product.id(), null, "SALE", -1, 10, 9,
                "BAD-ACTOR", null, -1L)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inventory.record(product.id(), -1L, "SALE", -1, 10, 9,
                "BAD-ORDER", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        inventory.record(product.id(), null, "OPENING_BALANCE", 10, 0, 10, "UNIQUE", null, null);
        assertThatThrownBy(() -> inventory.record(product.id(), null, "OPENING_BALANCE", 10, 0, 10,
                "UNIQUE", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inventory.byProduct(product.id())).hasSize(1);
    }
}
