package vn.edu.sales.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.infrastructure.persistence.product.ProductRepositoryAdapter;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:b6-product-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false"
})
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProductPersistenceTest {

    @Autowired
    private ProductRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private Product activeKeyboard;
    private Product activeMouse;
    private Product activeHeadphone;
    private Product inactiveKeyboard;
    private Product inactiveMouse;

    @BeforeEach
    void prepareData() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }

        activeKeyboard = adapter.save(new Product(null, "KB-01", "Bàn phím cơ DareU",
                "Mô tả bàn phím cơ", new BigDecimal("800000"), 10, ProductStatus.ACTIVE, null));
        activeMouse = adapter.save(new Product(null, "MS-01", "Chuột không dây Logitech",
                "Mô tả chuột logitech", new BigDecimal("400000"), 20, ProductStatus.ACTIVE, null));
        activeHeadphone = adapter.save(new Product(null, "HP-01", "Tai nghe chụp tai Sony",
                "Mô tả tai nghe chụp tai", new BigDecimal("1500000"), 5, ProductStatus.ACTIVE, null));

        inactiveKeyboard = adapter.save(new Product(null, "KB-OLD", "Bàn phím văn phòng cũ",
                "Hàng thanh lý", new BigDecimal("100000"), 0, ProductStatus.INACTIVE, null));
        inactiveMouse = adapter.save(new Product(null, "MS-OLD", "Chuột có dây Mitsumi cũ",
                "Hàng thanh lý", new BigDecimal("50000"), 0, ProductStatus.INACTIVE, null));
    }

    @Test
    void searchWithEmptyQueryReturnsAllActiveProductsAndExcludesInactive() {
        ProductPage result = adapter.searchActive("", 0, 10);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items())
                .extracting(Product::sku)
                .containsExactly(activeHeadphone.sku(), activeMouse.sku(), activeKeyboard.sku())
                .doesNotContain(inactiveKeyboard.sku(), inactiveMouse.sku());
    }

    @Test
    void searchWithWhitespaceQueryReturnsAllActiveProductsAndExcludesInactive() {
        ProductPage result = adapter.searchActive("   ", 0, 10);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items())
                .extracting(Product::sku)
                .containsExactly(activeHeadphone.sku(), activeMouse.sku(), activeKeyboard.sku())
                .doesNotContain(inactiveKeyboard.sku(), inactiveMouse.sku());
    }

    @Test
    void searchWithKeywordMatchesNameOrSkuCaseInsensitiveAndExcludesInactive() {
        ProductPage nameMatch = adapter.searchActive("bàn phím", 0, 10);
        assertThat(nameMatch.totalElements()).isEqualTo(1);
        assertThat(nameMatch.items().getFirst().sku()).isEqualTo(activeKeyboard.sku());

        ProductPage skuMatchUpper = adapter.searchActive("MS-01", 0, 10);
        assertThat(skuMatchUpper.totalElements()).isEqualTo(1);
        assertThat(skuMatchUpper.items().getFirst().sku()).isEqualTo(activeMouse.sku());

        ProductPage skuMatchLower = adapter.searchActive("ms-01", 0, 10);
        assertThat(skuMatchLower.totalElements()).isEqualTo(1);
        assertThat(skuMatchLower.items().getFirst().sku()).isEqualTo(activeMouse.sku());
    }

    @Test
    void searchMatchingOnlyInactiveProductsReturnsEmptyPage() {
        ProductPage result = adapter.searchActive("cũ", 0, 10);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchPaginationWorksAccuratelyAtDatabaseLevel() {
        ProductPage page0 = adapter.searchActive("", 0, 2);
        assertThat(page0.page()).isZero();
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.items()).hasSize(2);

        ProductPage page1 = adapter.searchActive("", 1, 2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);
        assertThat(page1.totalElements()).isEqualTo(3);
        assertThat(page1.totalPages()).isEqualTo(2);
        assertThat(page1.items()).hasSize(1);

        ProductPage pageBeyond = adapter.searchActive("", 5, 2);
        assertThat(pageBeyond.page()).isEqualTo(5);
        assertThat(pageBeyond.size()).isEqualTo(2);
        assertThat(pageBeyond.totalElements()).isEqualTo(3);
        assertThat(pageBeyond.totalPages()).isEqualTo(2);
        assertThat(pageBeyond.items()).isEmpty();
    }
}
