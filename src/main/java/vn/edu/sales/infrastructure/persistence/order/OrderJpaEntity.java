package vn.edu.sales.infrastructure.persistence.order;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.edu.sales.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String orderCode;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String recipientPhone;

    @Column(nullable = false, length = 500)
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id asc")
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public OrderJpaEntity(Long id, String orderCode, Long userId, String recipientName,
                          String recipientPhone, String shippingAddress, OrderStatus status,
                          BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderCode = orderCode;
        this.userId = userId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.shippingAddress = shippingAddress;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addItem(OrderItemJpaEntity item) {
        item.attachTo(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public Long getUserId() { return userId; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getShippingAddress() { return shippingAddress; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<OrderItemJpaEntity> getItems() { return items; }
}
