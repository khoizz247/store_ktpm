package vn.edu.sales.infrastructure.persistence.order;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_order_items_order_product", columnNames = {"order_id", "product_id"}))
public class OrderItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 160)
    private String productName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItemJpaEntity() {
    }

    public OrderItemJpaEntity(Long id, Long productId, String productName, BigDecimal unitPrice,
                              int quantity) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    void attachTo(OrderJpaEntity order) { this.order = order; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
