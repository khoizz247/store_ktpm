package vn.edu.sales.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.service.ProductService;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;
import vn.edu.sales.infrastructure.persistence.product.SpringDataProductRepository;
import vn.edu.sales.infrastructure.persistence.user.SpringDataUserRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:b7-audit-seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=true"
})
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProductAuditAndSeedIT {

    @Autowired
    private CommandLineRunner seedData;

    @Autowired
    private SpringDataUserRepository springDataUserRepository;

    @Autowired
    private SpringDataProductRepository springDataProductRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryStore inventoryStore;

    @Autowired
    private JdbcTemplate jdbc;

    private User adminActor;

    @BeforeEach
    void prepareData() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }

        adminActor = userRepository.save(new User(null, "admin-audit@example.test", "hashed_pwd",
                "Audit Admin", Role.ADMIN, UserStatus.ACTIVE, null));
    }

    @Test
    void repeatedSeedExecutionDoesNotProduceDuplicateSkuOrViolateConstraints() throws Exception {

        seedData.run();
        assertThat(springDataProductRepository.count()).isEqualTo(2);
        assertThat(springDataProductRepository.existsBySku("KB-001")).isTrue();
        assertThat(springDataProductRepository.existsBySku("MS-001")).isTrue();

        seedData.run();
        seedData.run();

        assertThat(springDataProductRepository.count()).isEqualTo(2);
        Integer kbCount = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku='KB-001'", Integer.class);
        Integer msCount = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku='MS-001'", Integer.class);
        assertThat(kbCount).isEqualTo(1);
        assertThat(msCount).isEqualTo(1);
    }

    @Test
    void businessOperationsRecordAccurateAuditActorAndDeltaInDatabase() {
        Product created = productService.create(adminActor.email(), "AUDIT-01", "Bàn phím cơ Audit",
                "Mô tả sản phẩm", new BigDecimal("500000"), 20);

        List<InventoryStore.Movement> movements = inventoryStore.byProduct(created.id());
        assertThat(movements).hasSize(1);
        InventoryStore.Movement openMv = movements.getFirst();
        assertThat(openMv.type()).isEqualTo("OPENING_BALANCE");
        assertThat(openMv.quantityChange()).isEqualTo(20);
        assertThat(openMv.stockBefore()).isZero();
        assertThat(openMv.stockAfter()).isEqualTo(20);
        assertThat(openMv.actorUserId()).isEqualTo(adminActor.id());

        Product zeroStockProduct = productService.create(adminActor.email(), "AUDIT-ZERO", "Sản phẩm 0 tồn",
                "Desc", new BigDecimal("200000"), 0);
        assertThat(inventoryStore.byProduct(zeroStockProduct.id())).isEmpty();

        productService.update(adminActor.email(), created.id(), "Bàn phím cơ Audit (Đã sửa)",
                "Mô tả mới", new BigDecimal("550000"), 20);
        assertThat(inventoryStore.byProduct(created.id())).hasSize(1);

        productService.update(adminActor.email(), created.id(), "Bàn phím cơ Audit (Tăng tồn)",
                "Mô tả mới", new BigDecimal("550000"), 25);
        movements = inventoryStore.byProduct(created.id());
        assertThat(movements).hasSize(2);
        InventoryStore.Movement adjIn = movements.get(1);
        assertThat(adjIn.type()).isEqualTo("ADJUSTMENT_IN");
        assertThat(adjIn.quantityChange()).isEqualTo(5);
        assertThat(adjIn.stockBefore()).isEqualTo(20);
        assertThat(adjIn.stockAfter()).isEqualTo(25);
        assertThat(adjIn.actorUserId()).isEqualTo(adminActor.id());

        productService.updateStock(adminActor.email(), created.id(), 25);
        assertThat(inventoryStore.byProduct(created.id())).hasSize(2);

        productService.updateStock(adminActor.email(), created.id(), 17);
        movements = inventoryStore.byProduct(created.id());
        assertThat(movements).hasSize(3);
        InventoryStore.Movement adjOut = movements.get(2);
        assertThat(adjOut.type()).isEqualTo("ADJUSTMENT_OUT");
        assertThat(adjOut.quantityChange()).isEqualTo(-8);
        assertThat(adjOut.stockBefore()).isEqualTo(25);
        assertThat(adjOut.stockAfter()).isEqualTo(17);
        assertThat(adjOut.actorUserId()).isEqualTo(adminActor.id());

        productService.delete(created.id());
        Product inDb = springDataProductRepository.findById(created.id()).map(e ->
                new Product(e.getId(), e.getSku(), e.getName(), e.getDescription(), e.getPrice(),
                        e.getStockQuantity(), e.getStatus(), e.getVersion())).orElseThrow();
        assertThat(inDb.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(inDb.stockQuantity()).isEqualTo(17);
        assertThat(inventoryStore.byProduct(created.id())).hasSize(3);
    }
}
