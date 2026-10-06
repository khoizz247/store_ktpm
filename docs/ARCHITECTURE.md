# Kiến trúc mục tiêu

Dự án phục vụ một cửa hàng, một số tồn kho cho mỗi sản phẩm.

```text
api                 HTTP, DTO và ánh xạ response
application/service Quy tắc nghiệp vụ
application/port/out Hợp đồng repository, token, hash và transaction
domain              Model và exception
infrastructure      JPA/JDBC, JWT, BCrypt, bootstrap
config              Nối bean, bảo mật và OpenAPI
```

Service chỉ phụ thuộc model và port. Code web/DB nằm ở api/infrastructure.
Base hiện có application entry point, API health và Swagger. Các tầng còn lại
được thêm theo từng PR có test và người review.

Phạm vi cuối: đăng nhập, sản phẩm, kho, giỏ và đơn. Database cuối có users,
products, orders, order_items, cart_items, inventory_movements.
