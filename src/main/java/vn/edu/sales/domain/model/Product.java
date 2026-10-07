package vn.edu.sales.domain.model;

import java.math.BigDecimal;

public record Product(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status,
        Long version
) {
    public Product {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("SKU không được để trống");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Giá phải lớn hơn 0");
        if (stockQuantity < 0) throw new IllegalArgumentException("Tồn kho không được âm");
        if (status == null) status = ProductStatus.ACTIVE;
    }

    public Product withStock(int newStock) {
        return new Product(id, sku, name, description, price, newStock, status, version);
    }

    public Product inactive() {
        return new Product(id, sku, name, description, price, stockQuantity, ProductStatus.INACTIVE, version);
    }
}
