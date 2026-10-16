package vn.edu.sales.application.services;

import vn.edu.sales.application.port.out.CartStore;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.model.Order;
import vn.edu.sales.domain.model.ProductStatus;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.UserStatus;

import java.util.List;
import java.util.Locale;

public class CartService {
    private final CartStore carts;
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderService orders;
    private final TransactionRunner transactions;

    public CartService(CartStore carts, UserRepository users, ProductRepository products,
                       OrderService orders, TransactionRunner transactions) {
        this.carts = carts;
        this.users = users;
        this.products = products;
        this.orders = orders;
        this.transactions = transactions;
    }

    public List<CartStore.Line> mine(String email) { return carts.findByUser(customerId(email)); }

    public List<CartStore.Line> put(String email, Long productId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
        return transactions.execute(() -> {
            Long userId = customerId(email);
            var product = products.findById(productId).filter(p -> p.status() == ProductStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));
            if (quantity > product.stockQuantity())
                throw new BusinessConflictException("Số lượng vượt tồn kho hiện tại");
            carts.put(userId, productId, quantity);
            return carts.findByUser(userId);
        });
    }

    public void remove(String email, Long productId) { carts.remove(customerId(email), productId); }

    public void clear(String email) { carts.clear(customerId(email)); }

    public Order checkout(String email, String recipientName, String recipientPhone, String shippingAddress) {
        return transactions.execute(() -> {
            Long userId = customerId(email);
            List<CartStore.Line> lines = carts.findByUserForUpdate(userId);
            if (lines.isEmpty()) throw new BusinessConflictException("Giỏ hàng trống");
            Order order = orders.create(email, recipientName, recipientPhone, shippingAddress,
                    lines.stream().map(line -> new OrderService.CreateLine(line.productId(), line.quantity())).toList());
            carts.clear(userId);
            return order;
        });
    }

    private Long customerId(String email) {
        return users.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> user.role() == Role.CUSTOMER && user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng đang hoạt động")).id();
    }
}
