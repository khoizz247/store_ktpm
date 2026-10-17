package vn.edu.sales.application.service;

import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.model.Product;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InventoryService {
    private final InventoryStore movements;
    private final ProductRepository products;
    private final UserRepository users;
    private final TransactionRunner transactions;

    public InventoryService(InventoryStore movements, ProductRepository products, UserRepository users,
                            TransactionRunner transactions) {
        this.movements = movements;
        this.products = products;
        this.users = users;
        this.transactions = transactions;
    }

    public List<InventoryStore.Movement> byProduct(Long productId) {
        product(productId);
        return movements.byProduct(productId);
    }

    public InventoryStore.Movement adjust(String actorEmail, Long productId, int change, String note) {
        return transactions.execute(() -> {
            if (change == 0) throw new IllegalArgumentException("Biến động kho phải khác 0");
            Product before = products.findAllByIdsForUpdate(List.of(productId)).stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));
            long after = (long) before.stockQuantity() + change;
            if (after < 0 || after > Integer.MAX_VALUE)
                throw new BusinessConflictException("Tồn kho sau điều chỉnh không hợp lệ");
            Long actorId = users.findByEmail(actorEmail.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng")).id();
            products.save(before.withStock((int) after));
            String reference = "ADJ-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase(Locale.ROOT);
            movements.record(productId, null, change > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT",
                    change, before.stockQuantity(), (int) after, reference, note, actorId);
            return movements.byProduct(productId).stream()
                    .filter(movement -> reference.equals(movement.referenceCode())).findFirst().orElseThrow();
        });
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
    }
}
