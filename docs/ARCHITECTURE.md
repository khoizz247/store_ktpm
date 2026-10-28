# Kiến trúc hiện tại

Dự án phục vụ một cửa hàng, một số tồn kho cho mỗi sản phẩm.

```text
api                 HTTP, DTO và ánh xạ response
application/service(s) Quy tắc nghiệp vụ
application/port/out Hợp đồng repository, token, hash và transaction
domain              Model và exception
infrastructure      JPA/JDBC, JWT, BCrypt, bootstrap
config              Nối bean, bảo mật và OpenAPI
```

Service chỉ phụ thuộc model và port. Code web/DB nằm ở api/infrastructure.
Các tầng đã có auth/user, product/inventory, order và cart. Controller admin đơn
C6 đã tích hợp. CurrentUserController gọi CurrentUserService, không đọc repository
trực tiếp. Tên package hiện có cả application.service và application.services;
đây là khác biệt cách đặt tên, không phải hai tầng nghiệp vụ khác nhau.

Phạm vi: đăng nhập, sản phẩm, kho, giỏ và đơn. Database có users,
products, orders, order_items, cart_items, inventory_movements.

`ArchitectureTest` dùng ArchUnit để kiểm tra dependency giữa các package
production. Đây là kiểm tra tự động bổ sung cho review, không thay test nghiệp vụ.
Maven verify chạy cả Test và IT; workflow còn build database MySQL mới để kiểm tra
Flyway/deployment. Xem [CI_ARCHITECTURE.md](CI_ARCHITECTURE.md).

## Các luồng chính

| Chức năng | Controller → Service | Port → Adapter |
|---|---|---|
| Đăng nhập/đăng ký | AuthController → AuthService | UserRepository/PasswordHasher/TokenProvider → UserRepositoryAdapter/BcryptPasswordHasher/JwtTokenProvider |
| Xem tôi/đổi mật khẩu | CurrentUserController/PasswordController → CurrentUserService/PasswordChangeService | UserRepository, PasswordHasher, TransactionRunner |
| Sản phẩm/kho | ProductController/InventoryController → ProductService/InventoryService | ProductRepository/InventoryStore → ProductRepositoryAdapter/JdbcInventoryStore |
| Đặt/hủy/quản lý đơn | OrderController/AdminOrderController → OrderService | OrderRepository/ProductRepository/OrderSearchStore/InventoryStore → JPA + JDBC adapters |
| Giỏ/checkout | CartController → CartService → OrderService | CartStore → JdbcCartStore, cùng transaction runner |

Filter JWT chạy trước controller. SecurityConfig áp quyền theo đường dẫn/method;
service vẫn kiểm tra owner, trạng thái đơn và tồn kho. JWT không thay kiểm tra
nghiệp vụ. SpringTransactionRunner dùng TransactionTemplate; JPA và JDBC cùng
datasource nên rollback cả đơn, kho, audit và xóa giỏ nếu checkout thất bại.

H2: Hibernate tạo bốn bảng JPA, schema-h2.sql tạo hai bảng JDBC.
MySQL: Flyway V1/V2 tạo schema/seed; Hibernate chỉ validate, không tự sửa schema.
Không sửa migration đã áp dụng; thay đổi schema sau này phải thêm V3 trở đi.

Danh sách đơn tìm ID rồi đọc từng đơn là baseline còn nhiều query. Khóa sản phẩm
đảm bảo không bán vượt kho nhưng có thể khiến người mua cùng sản phẩm phải chờ.
Đây là giới hạn cần đo ở Pha 2, không phải lý do bỏ transaction ở Pha 1.
