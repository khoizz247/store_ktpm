package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    List<Product> findAllActive();

    ProductPage searchActive(String query, int page, int size);

    Optional<Product> findById(Long id);

    List<Product> findAllByIdsForUpdate(List<Long> ids);

    List<Product> saveAll(List<Product> products);

    boolean existsBySku(String sku);
}
