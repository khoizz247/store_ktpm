package vn.edu.sales.application.service;

import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
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
        return service(repository, new NoOpInventoryStore());
    }

    private ProductService service(InMemoryProductRepository repository, InventoryStore inventory) {
        return new ProductService(repository, inventory, new TransactionRunner() {
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

        assertThatThrownBy(() -> service.search("a", -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page phải >= 0");
        assertThatThrownBy(() -> service.search("a", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size trong khoảng 1-100");
        assertThatThrownBy(() -> service.search("a", 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size trong khoảng 1-100");
    }

    @Test
    void searchSupportsBoundaryPageAndSizes() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        ProductService service = service(repository);

        for (int i = 1; i <= 5; i++) {
            service.create("admin@example.com", "SKU-00" + i, "Sản phẩm " + i, "", new BigDecimal("100000"), 10);
        }

        ProductPage page1 = service.search("", 0, 1);
        assertThat(page1.page()).isZero();
        assertThat(page1.size()).isEqualTo(1);
        assertThat(page1.items()).hasSize(1);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.totalPages()).isEqualTo(5);

        ProductPage page100 = service.search("", 0, 100);
        assertThat(page100.page()).isZero();
        assertThat(page100.size()).isEqualTo(100);
        assertThat(page100.items()).hasSize(5);
        assertThat(page100.totalElements()).isEqualTo(5);
        assertThat(page100.totalPages()).isEqualTo(1);
    }

    @Test
    void searchBeyondLastPageReturnsEmptyListWithAccurateMetadata() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        ProductService service = service(repository);

        for (int i = 1; i <= 3; i++) {
            service.create("admin@example.com", "SKU-P" + i, "Product " + i, "", new BigDecimal("50000"), 5);
        }

        ProductPage pageBeyond = service.search("", 10, 2);
        assertThat(pageBeyond.page()).isEqualTo(10);
        assertThat(pageBeyond.size()).isEqualTo(2);
        assertThat(pageBeyond.items()).isEmpty();
        assertThat(pageBeyond.totalElements()).isEqualTo(3);
        assertThat(pageBeyond.totalPages()).isEqualTo(2);
    }


    @Test
    void createProductWithZeroStockDoesNotRecordInventoryDelta() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RecordingInventoryStore inventoryStore = new RecordingInventoryStore();
        ProductService service = service(repository, inventoryStore);

        Product product = service.create("admin@example.com", "SKU-Z1", "Zero Stock", "Zero", new BigDecimal("50000"), 0);

        assertThat(product.stockQuantity()).isZero();
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(inventoryStore.all()).isEmpty();
    }

    @Test
    void createProductWithPositiveStockRecordsOpeningBalanceDelta() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RecordingInventoryStore inventoryStore = new RecordingInventoryStore();
        ProductService service = service(repository, inventoryStore);

        Product product = service.create("admin@example.com", "SKU-POS", "Positive Stock", "Positive", new BigDecimal("50000"), 10);

        assertThat(product.stockQuantity()).isEqualTo(10);
        assertThat(inventoryStore.all()).hasSize(1);
        var movement = inventoryStore.all().getFirst();
        assertThat(movement.productId()).isEqualTo(product.id());
        assertThat(movement.type()).isEqualTo("OPENING_BALANCE");
        assertThat(movement.quantityChange()).isEqualTo(10);
        assertThat(movement.stockBefore()).isZero();
        assertThat(movement.stockAfter()).isEqualTo(10);
        assertThat(movement.actorUserId()).isEqualTo(1L);
    }

    @Test
    void updateAndPatchWithoutStockChangeDoNotRecordInventoryMovement() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RecordingInventoryStore inventoryStore = new RecordingInventoryStore();
        ProductService service = service(repository, inventoryStore);

        Product product = service.create("admin@example.com", "SKU-UPD", "Initial", "Initial", new BigDecimal("100000"), 10);
        assertThat(inventoryStore.all()).hasSize(1);

        Product updated = service.update("admin@example.com", product.id(), "Updated Name", "Updated Desc", new BigDecimal("120000"), 10);
        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(updated.stockQuantity()).isEqualTo(10);
        assertThat(inventoryStore.all()).hasSize(1);

        Product patched = service.updateStock("admin@example.com", product.id(), 10);
        assertThat(patched.stockQuantity()).isEqualTo(10);
        assertThat(inventoryStore.all()).hasSize(1);
    }

    @Test
    void updateAndPatchWithStockChangeRecordCorrectDeltaAndMovement() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RecordingInventoryStore inventoryStore = new RecordingInventoryStore();
        ProductService service = service(repository, inventoryStore);

        Product product = service.create("admin@example.com", "SKU-ADJ", "Item", "", new BigDecimal("100000"), 10);
        assertThat(inventoryStore.all()).hasSize(1);

        service.update("admin@example.com", product.id(), "Item Renamed", "", new BigDecimal("100000"), 14);
        assertThat(inventoryStore.all()).hasSize(2);
        var inMovement = inventoryStore.all().get(1);
        assertThat(inMovement.type()).isEqualTo("ADJUSTMENT_IN");
        assertThat(inMovement.quantityChange()).isEqualTo(4);
        assertThat(inMovement.stockBefore()).isEqualTo(10);
        assertThat(inMovement.stockAfter()).isEqualTo(14);

        service.updateStock("admin@example.com", product.id(), 9);
        assertThat(inventoryStore.all()).hasSize(3);
        var outMovement = inventoryStore.all().get(2);
        assertThat(outMovement.type()).isEqualTo("ADJUSTMENT_OUT");
        assertThat(outMovement.quantityChange()).isEqualTo(-5);
        assertThat(outMovement.stockBefore()).isEqualTo(14);
        assertThat(outMovement.stockAfter()).isEqualTo(9);
    }

    @Test
    void statusAndStockQuantityAreIndependent() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RecordingInventoryStore inventoryStore = new RecordingInventoryStore();
        ProductService service = service(repository, inventoryStore);

        Product zeroStockProduct = service.create("admin@example.com", "SKU-INDEP-0", "Zero Item", "", new BigDecimal("50000"), 0);
        assertThat(zeroStockProduct.stockQuantity()).isZero();
        assertThat(zeroStockProduct.status()).isEqualTo(ProductStatus.ACTIVE);

        Product product = service.create("admin@example.com", "SKU-INDEP-1", "Indep Item", "", new BigDecimal("100000"), 15);
        int movementsBeforeDelete = inventoryStore.all().size();

        service.delete(product.id());

        Product deletedInRepo = repository.findById(product.id()).orElseThrow();
        assertThat(deletedInRepo.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(deletedInRepo.stockQuantity()).isEqualTo(15);

        assertThat(inventoryStore.all().size()).isEqualTo(movementsBeforeDelete);

        assertThatThrownBy(() -> service.getById(product.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        ProductPage searchResult = service.search("Indep Item", 0, 10);
        assertThat(searchResult.items()).isEmpty();
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

    private static class RecordingInventoryStore implements InventoryStore {
        private final List<Movement> movements = new ArrayList<>();

        @Override
        public void record(Long productId, Long orderId, String type, int change, int before,
                           int after, String reference, String note, Long actorUserId) {
            movements.add(new Movement((long) movements.size() + 1, productId, orderId, type, change,
                    before, after, reference, note, actorUserId, java.time.LocalDateTime.now()));
        }

        @Override
        public List<Movement> byProduct(Long productId) {
            return movements.stream().filter(m -> m.productId().equals(productId)).toList();
        }

        public List<Movement> all() {
            return movements;
        }
    }

    private static class InMemoryProductRepository implements ProductRepository {
        private final List<Product> products = new ArrayList<>();

        @Override
        public Product save(Product product) {
            if (product.id() != null) {
                products.removeIf(p -> p.id().equals(product.id()));
                products.add(product);
                return product;
            }
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

        @Override
        public ProductPage searchActive(String query, int page, int size) {
            List<Product> matches = products.stream()
                    .filter(product -> product.status() == ProductStatus.ACTIVE)
                    .filter(product -> query == null || query.isBlank()
                            || product.name().toLowerCase().contains(query.toLowerCase())
                            || product.sku().toLowerCase().contains(query.toLowerCase()))
                    .toList();
            long totalElements = matches.size();
            int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
            int fromIndex = page * size;
            List<Product> pageItems;
            if (fromIndex >= matches.size()) {
                pageItems = List.of();
            } else {
                int toIndex = Math.min(fromIndex + size, matches.size());
                pageItems = matches.subList(fromIndex, toIndex);
            }
            return new ProductPage(pageItems, page, size, totalElements, totalPages);
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
        public List<Product> saveAll(List<Product> productsToSave) {
            return productsToSave.stream().map(this::save).toList();
        }

        @Override
        public boolean existsBySku(String sku) {
            return products.stream().anyMatch(product -> product.sku().equals(sku));
        }
    }
}
