package vn.edu.sales.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Order(Long id, String orderCode, Long userId, String recipientName,
                    String recipientPhone, String shippingAddress, OrderStatus status,
                    BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt,
                    List<OrderItem> items) {
    public Order{
        if (userId == null) {
            throw new IllegalArgumentException("Khách hàng không hợp lệ");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Tên người nhận không được để trống");
        }
        if (recipientPhone == null || recipientPhone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ không được để trống");
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất một sản phẩm");
        }
        if (totalAmount == null) {
            totalAmount = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
