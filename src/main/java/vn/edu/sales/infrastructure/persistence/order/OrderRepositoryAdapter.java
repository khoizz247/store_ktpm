package vn.edu.sales.infrastructure.persistence.order;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.OrderRepository;
import vn.edu.sales.domain.model.Order;
import vn.edu.sales.domain.model.OrderItem;
import vn.edu.sales.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepositoryAdapter implements OrderRepository {
    private final SpringDataOrderRepository repository;
    private final EntityManager entityManager;

    public OrderRepositoryAdapter(SpringDataOrderRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity saved = repository.saveAndFlush(toEntity(order));
        entityManager.refresh(saved);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return repository.findWithItemsById(id).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(Long userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Order> findAll(OrderStatus status) {
        List<OrderJpaEntity> entities = status == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findAllByStatusOrderByCreatedAtDesc(status);
        return entities.stream().map(this::toDomain).toList();
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(order.id(), order.orderCode(), order.userId(),
                order.recipientName(), order.recipientPhone(), order.shippingAddress(), order.status(),
                order.totalAmount(), order.createdAt(), order.updatedAt());
        order.items().forEach(item -> entity.addItem(new OrderItemJpaEntity(item.id(), item.productId(),
                item.productName(), item.unitPrice(), item.quantity())));
        return entity;
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream().map(item -> new OrderItem(item.getId(),
                item.getProductId(), item.getProductName(), item.getUnitPrice(), item.getQuantity(),
                item.getLineTotal())).toList();
        return new Order(entity.getId(), entity.getOrderCode(), entity.getUserId(), entity.getRecipientName(),
                entity.getRecipientPhone(), entity.getShippingAddress(), entity.getStatus(), entity.getTotalAmount(),
                entity.getCreatedAt(), entity.getUpdatedAt(), items);
    }
}
