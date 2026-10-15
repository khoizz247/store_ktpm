package vn.edu.sales.application.port.out;

import java.math.BigDecimal;
import java.util.List;

public interface CartStore {
    record Line(Long productId, String sku, String name, BigDecimal unitPrice,
                int stockQuantity, String productStatus, int quantity) {}

    List<Line> findByUser(Long userId);
    List<Line> findByUserForUpdate(Long userId);
    void put(Long userId, Long productId, int quantity);
    void remove(Long userId, Long productId);
    void clear(Long userId);
}
