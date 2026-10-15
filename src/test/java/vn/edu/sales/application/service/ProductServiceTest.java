package vn.edu.sales.application.service;

import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.UserStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceTest {

    private ProductService service(InMemoryProductRepository repository) {
        return new ProductService(repository, new NoOpInventoryStore(), new TransactionRunner() {
            @Override public <T> T execute(java.util.function.Supplier<T> work) { return work.get(); }
        }, new InMemoryAdminRepository());
    }

    @Test
    void createsAValidProductWithoutDependingOnSpringOrJpa() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        ProductService service = service(repository);

        Product product = service.create("admin@example.com", "sku-001", "  Sản phẩm A  ", "Mô tả", new BigDecimal("120000"), 5);

        assertThat(product.id()).isEqualTo(1L);
        assertThat(product.name()).isEqualTo("Sản phẩm A");
        assertThat(product.sku()).isEqualTo("SKU-001");
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateSkuAfterNormalization() {
        ProductService service = service(new InMemoryProductRepository());
        service.create("admin@example.com", "sku-001", "Sản phẩm A", "", new BigDecimal("100"), 1);

        assertThatThrownBy(() -> service.create("admin@example.com", " SKU-001 ", "Sản phẩm B", "",
                new BigDecimal("100"), 1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void rejectsInvalidSearchPagination() {
        ProductService service = service(new InMemoryProductRepository());

        assertThatThrownBy(() -> service.search("a", -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("a", 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    private static class InMemoryAdminRepository implements UserRepository {
        @Override public User save(User user) { return user; }
        @Override public Optional<User> findByEmail(String email) {
            return Optional.of(new User(1L, email, "hash", "Admin", Role.ADMIN, UserStatus.ACTIVE, null));
        }
        @Override public Optional<User> findById(Long id) { return Optional.empty(); }
        @Override public boolean existsByEmail(String email) { return false; }
        @Override public long count() { return 1; }
        @Override public void updatePassword(Long id, String hash) {}
    }

    private static class NoOpInventoryStore implements InventoryStore {
        @Override public void record(Long productId, Long orderId, String type, int change, int before,
                                     int after, String reference, String note, Long actorUserId) {}
        @Override public List<Movement> byProduct(Long productId) { return List.of(); }
    }

    private static class InMemoryProductRepository implements ProductRepository {
        private final List<Product> products = new ArrayList<>();

        @Override
        public Product save(Product product) {
            Product saved = new Product(
                    (long) products.size() + 1,
                    product.sku(),
                    product.name(),
                    product.description(),
                    product.price(),
                    product.stockQuantity(),
                    product.status(),
                    0L
            );
            products.add(saved);
            return saved;
        }

        @Override
        public List<Product> findAllActive() {
            return products.stream().filter(product -> product.status() == ProductStatus.ACTIVE).toList();
        }

        @Override public ProductPage searchActive(String query, int page, int size) {
            return new ProductPage(findAllActive(), page, size, findAllActive().size(), 1);
        }

        @Override
        public Optional<Product> findById(Long id) {
            return products.stream().filter(product -> product.id().equals(id)).findFirst();
        }

        @Override
        public List<Product> findAllByIdsForUpdate(List<Long> ids) {
            return products.stream().filter(product -> ids.contains(product.id())).toList();
        }

        @Override
        public List<Product> saveAll(List<Product> products) {
            return products.stream().map(this::save).toList();
        }

        @Override
        public boolean existsBySku(String sku) {
            return products.stream().anyMatch(product -> product.sku().equals(sku));
        }
    }
}
