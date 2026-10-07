package vn.edu.sales.domain.model;

import java.math.BigDecimal;

public record OrderItem(Long id, Long productId, String productName, BigDecimal unitPrice,
                        int quantity, BigDecimal lineTotal) {
    public OrderItem {
        if (productId == null) {
            throw new IllegalArgumentException("Sản phẩm không hợp lệ");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Đơn giá không hợp lệ");
        }
        if (lineTotal == null) {
            lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}