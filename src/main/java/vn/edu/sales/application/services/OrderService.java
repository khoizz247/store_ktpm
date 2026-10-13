package vn.edu.sales.application.services;

import vn.edu.sales.application.port.out.*;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TransactionRunner transactionRunner;
    private final InventoryStore inventory;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, TransactionRunner transactionRunner, InventoryStore inventory) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
        this.inventory = inventory;
    }

    public Order create(String customerEmail, String recipientName, String recipientPhone,
                        String shippingAddress, List<CreateLine> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Đơn hàng phải có sản phẩm");
        Set<Long> uniqueIds = lines.stream().map(CreateLine::productId).collect(Collectors.toSet());
        if (uniqueIds.size() != lines.size()) throw new IllegalArgumentException("Không được lặp sản phẩm trong đơn hàng");

        return transactionRunner.execute(() -> {
            User user = activeUser(customerEmail);
            List<Long> ids = uniqueIds.stream().sorted().toList();
            Map<Long, Product> products = productRepository.findAllByIdsForUpdate(ids).stream()
                    .collect(Collectors.toMap(Product::id, Function.identity()));
            List<Product> changedProducts = new ArrayList<>();
            List<OrderItem> items = new ArrayList<>();

            for (CreateLine line : lines) {
                if (line.quantity() <= 0) throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
                Product product = products.get(line.productId());
                if (product == null || product.status() != ProductStatus.ACTIVE) {
                    throw new ResourceNotFoundException("Không tìm thấy sản phẩm: " + line.productId());
                }
                if (product.stockQuantity() < line.quantity()) {
                    throw new BusinessConflictException("Không đủ tồn kho cho sản phẩm: " + product.name());
                }
                changedProducts.add(product.withStock(product.stockQuantity() - line.quantity()));
                items.add(new OrderItem(null, product.id(), product.name(), product.price(),
                        line.quantity(), product.price().multiply(BigDecimal.valueOf(line.quantity()))));
            }

            productRepository.saveAll(changedProducts);
            Order saved = orderRepository.save(new Order(null, newOrderCode(), user.id(), recipientName,
                    recipientPhone, shippingAddress, OrderStatus.PENDING, null, null, null, items));
            for (CreateLine line : lines) {
                Product product = products.get(line.productId());
                inventory.record(product.id(), saved.id(), "SALE", -line.quantity(), product.stockQuantity(),
                        product.stockQuantity() - line.quantity(), saved.orderCode() + "-SALE-" + product.id(),
                        "Đặt hàng", user.id());
            }
            return saved;
        });
    }

    private User activeUser(String email) {
        return userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));
    }

    private String newOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "")
                            .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    public record CreateLine(Long productId, int quantity) {
    }
}
