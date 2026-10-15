package vn.edu.sales.application.service;

import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.UserStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductService {
    private final ProductRepository productRepository;
    private final InventoryStore inventory;
    private final TransactionRunner transactions;
    private final UserRepository users;

    public ProductService(ProductRepository productRepository, InventoryStore inventory,
                          TransactionRunner transactions, UserRepository users) {
        this.productRepository = productRepository;
        this.inventory = inventory;
        this.transactions = transactions;
        this.users = users;
    }

    public List<Product> getAllActive() {
        return productRepository.findAllActive();
    }

    public ProductPage search(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page phải >= 0 và size trong khoảng 1-100");
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("Từ khóa quá dài");
        return productRepository.searchActive(normalized, page, size);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .filter(product -> product.status() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
    }

    public Product create(String actorEmail, String sku, String name, String description, BigDecimal price, int stockQuantity) {
        return transactions.execute(() -> {
            Long actorId = activeAdminId(actorEmail);
            String normalizedSku = normalizeSku(sku);
            if (productRepository.existsBySku(normalizedSku)) {
                throw new BusinessConflictException("SKU đã tồn tại: " + normalizedSku);
            }
            Product product = new Product(null, normalizedSku, name.trim(), normalizeDescription(description),
                    price, stockQuantity, ProductStatus.ACTIVE, null);
            Product saved = productRepository.save(product);
            if (stockQuantity > 0) inventory.record(saved.id(), null, "OPENING_BALANCE", stockQuantity,
                    0, stockQuantity, newReference("OPEN"), "Tồn kho ban đầu", actorId);
            return saved;
        });
    }

    public Product update(String actorEmail, Long id, String name, String description, BigDecimal price, int stockQuantity) {
        return transactions.execute(() -> {
            Long actorId = activeAdminId(actorEmail);
            Product existing = locked(id);
            Product saved = productRepository.save(new Product(existing.id(), existing.sku(), name.trim(),
                    normalizeDescription(description), price, stockQuantity, existing.status(), existing.version()));
            recordAdjustment(existing, stockQuantity, actorId);
            return saved;
        });
    }

    public Product updateStock(String actorEmail, Long id, int stockQuantity) {
        return transactions.execute(() -> {
            Long actorId = activeAdminId(actorEmail);
            Product existing = locked(id);
            Product saved = productRepository.save(existing.withStock(stockQuantity));
            recordAdjustment(existing, stockQuantity, actorId);
            return saved;
        });
    }

    public void delete(Long id) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
        productRepository.save(existing.inactive());
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Product locked(Long id) {
        return productRepository.findAllByIdsForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
    }

    private void recordAdjustment(Product existing, int nextStock, Long actorId) {
        int difference = nextStock - existing.stockQuantity();
        if (difference != 0) inventory.record(existing.id(), null,
                difference > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT", difference,
                existing.stockQuantity(), nextStock, newReference("ADJ"), "Cập nhật sản phẩm", actorId);
    }

    private Long activeAdminId(String email) {
        return users.findByEmail(email.toLowerCase(java.util.Locale.ROOT))
                .filter(user -> user.role() == Role.ADMIN && user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ADMIN đang hoạt động"))
                .id();
    }

    private String newReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    }
}
