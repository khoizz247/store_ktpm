CREATE TABLE IF NOT EXISTS  inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    movement_type ENUM('OPENING_BALANCE','PURCHASE','SALE','SALE_REVERSAL','ADJUSTMENT_IN','ADJUSTMENT_OUT') NOT NULL,
    quantity_change INT NOT NULL,
    stock_before INT NOT NULL,
    stock_after INT NOT NULL,
    reference_code VARCHAR(80) NULL UNIQUE,
    note VARCHAR(500) NULL,
    created_by_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inventory_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_inventory_actor FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_inventory_delta CHECK (quantity_change <> 0 AND stock_before >= 0 AND stock_after >= 0
        AND stock_after = stock_before + quantity_change),
    INDEX idx_inventory_product_time (product_id, created_at)
) ENGINE=InnoDB;