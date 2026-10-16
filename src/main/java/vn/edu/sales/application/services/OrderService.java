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
    private final OrderSearchStore orderSearchStore;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, TransactionRunner transactionRunner,
                        InventoryStore inventory, OrderSearchStore orderSearchStore) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
        this.inventory = inventory;
        this.orderSearchStore = orderSearchStore;
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

    public OrderPage searchMine(String email, OrderStatus status, String code, int page, int size) {
        return search(activeUser(email).id(), status, code, page, size);
    }

    private User activeUser(String email) {
        User user = userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));

        if (user.status() != UserStatus.ACTIVE) {
            throw new BusinessConflictException("Tài khoản khách hàng không ở trạng thái hoạt động");
        }

        return user;
    }

    private String newOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "")
                            .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private OrderPage search(Long userId, OrderStatus status, String code, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page phải >= 0 và size trong khoảng 1-100");
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.length() > 32) throw new IllegalArgumentException("Mã đơn hàng quá dài");
        OrderSearchStore.Result result = orderSearchStore.search(userId, status, normalizedCode, page, size);
        List<Order> items = result.orderIds().stream().map(id -> orderRepository.findById(id).orElseThrow()).toList();
        int totalPages = (int) ((result.totalElements() + size - 1) / size);
        return new OrderPage(items, page, size, result.totalElements(), totalPages);
    }

    public Order getMineById(String customerEmail, Long id) {
        User user = activeUser(customerEmail);
        return orderRepository.findById(id).filter(order -> order.userId().equals(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + id));
    }

    public Order cancelMine(String customerEmail, Long id) {
        return transactionRunner.execute(() -> {
            User user = activeUser(customerEmail);
            Order order = orderRepository.findByIdForUpdate(id)
                    .filter(found -> found.userId().equals(user.id()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + id));
            if (order.status() != OrderStatus.PENDING)
                throw new BusinessConflictException("Khách hàng chỉ được hủy đơn đang PENDING");
            return changeStatusLocked(order, OrderStatus.CANCELLED, user.id());
        });
    }

    private Order changeStatusLocked(Order order, OrderStatus nextStatus, Long actorUserId) {
        if (order.status() == nextStatus) return order;
        validateTransition(order.status(), nextStatus);
        if (nextStatus == OrderStatus.CANCELLED) {
            restoreStock(order, actorUserId);
        }
        return orderRepository.save(order.withStatus(nextStatus));
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean allowed = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING || next == OrderStatus.CANCELLED;
            case SHIPPING -> next == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) throw new BusinessConflictException("Không thể chuyển trạng thái từ " + current + " sang " + next);
    }
    private void restoreStock(Order order, Long actorUserId) {
        List<Long> ids = order.items().stream().map(OrderItem::productId).sorted().toList();
        Map<Long, Product> products = productRepository.findAllByIdsForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        List<Product> changed = order.items().stream().map(item -> {
            Product product = products.get(item.productId());
            if (product == null) throw new BusinessConflictException("Không thể hoàn kho sản phẩm: " + item.productId());
            return product.withStock(product.stockQuantity() + item.quantity());
        }).toList();
        productRepository.saveAll(changed);
        for (OrderItem item : order.items()) {
            Product before = products.get(item.productId());
            inventory.record(before.id(), order.id(), "SALE_REVERSAL", item.quantity(), before.stockQuantity(),
                    before.stockQuantity() + item.quantity(), order.orderCode() + "-REVERSAL-" + before.id(),
                    "Hủy đơn hàng", actorUserId);
        }
    }


    public record CreateLine(Long productId, int quantity) {
    }

    public record OrderPage(List<Order> items, int page, int size, long totalElements, int totalPages) {}
}
