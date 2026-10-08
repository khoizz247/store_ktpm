-- Products are optional demo data; no shared admin password is seeded in MySQL.
INSERT INTO products (sku, name, description, price, stock_quantity, status) VALUES
    ('KB-001', 'Bàn phím cơ mẫu', 'Sản phẩm mẫu', 890000.00, 20, 'ACTIVE'),
    ('MS-001', 'Chuột không dây mẫu', 'Sản phẩm mẫu', 450000.00, 35, 'ACTIVE');

INSERT INTO inventory_movements
    (product_id, movement_type, quantity_change, stock_before, stock_after, reference_code, note)
SELECT id, 'OPENING_BALANCE', stock_quantity, 0, stock_quantity,
       CONCAT('DEMO-OPENING-', sku), 'Tồn kho sản phẩm mẫu'
FROM products
WHERE sku IN ('KB-001', 'MS-001');
