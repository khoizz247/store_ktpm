package vn.edu.sales.infrastructure.persistence.product;

import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepositoryAdapter implements ProductRepository {
    private final SpringDataProductRepository repository;

    public ProductRepositoryAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity(
                product.id(), product.sku(), product.name(), product.description(), product.price(),
                product.stockQuantity(), product.status(), product.version()
        );
        return toDomain(repository.save(entity));
    }

    @Override
    public List<Product> findAllActive() {
        return repository.findAllByStatusOrderByIdDesc(ProductStatus.ACTIVE).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public ProductPage searchActive(String query, int page, int size) {
        var result = repository.searchByStatus(ProductStatus.ACTIVE, query, PageRequest.of(page, size));
        return new ProductPage(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Optional<Product> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAllByIdsForUpdate(List<Long> ids) {
        return repository.findAllByIdsForUpdate(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Product> saveAll(List<Product> products) {
        List<ProductJpaEntity> entities = products.stream().map(product -> new ProductJpaEntity(
                product.id(), product.sku(), product.name(), product.description(), product.price(),
                product.stockQuantity(), product.status(), product.version())).toList();
        return repository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsBySku(String sku) {
        return repository.existsBySku(sku);
    }

    private Product toDomain(ProductJpaEntity entity) {
        return new Product(
                entity.getId(), entity.getSku(), entity.getName(), entity.getDescription(), entity.getPrice(),
                entity.getStockQuantity(), entity.getStatus(), entity.getVersion()
        );
    }
}
