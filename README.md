# Single Store Service

Backend quản lý bán hàng cho một cửa hàng, sử dụng Java và Spring Boot.
Phiên bản hiện tại có model/port, JPA adapter cho user/product/order,
JWT infrastructure và nền tảng transaction/biến động kho. API nghiệp vụ
đầy đủ đang được tích hợp; `/api/health` vẫn công khai.

## Công nghệ và chức năng hiện tại

- Java 21, Spring Boot 3.5.0, REST JSON và validation dependency.
- `GET /api/health`, Swagger, test khởi động/health và unit test AuthService.
- H2 mặc định: Hibernate tạo bốn bảng JPA trước, `schema-h2.sql` tạo
  `cart_items` và `inventory_movements` sau. Flyway và seed tắt ở H2.
- `SpringTransactionRunner` và `JdbcInventoryStore`, cùng integration test
  commit/rollback JPA + JDBC, audit và ràng buộc dữ liệu.
- Maven Wrapper, Dockerfile, Compose chỉ API và CI build/test.
- Tài liệu kiến trúc phân tầng.

Auth controller và nghiệp vụ tạo đơn C3 đã có; controller đơn vẫn là route thử,
API sản phẩm chưa được tích hợp trên main tại thời điểm gói này bắt đầu.
Adapter giỏ và tìm kiếm đơn đã sẵn sàng, chưa có API giỏ/search đơn.
Chi tiết: [CART_AND_ORDER_SEARCH.md](docs/CART_AND_ORDER_SEARCH.md).
Profile MySQL đã chuẩn bị cho `single_store_team` tại cổng `3309`, dùng
Flyway và Hibernate `validate`; Compose hiện vẫn chỉ chạy API với H2.
Hướng dẫn kiểm tra nền tảng dữ liệu: [DATABASE_FOUNDATION.md](docs/DATABASE_FOUNDATION.md).

## Chạy

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Yêu cầu JDK 21 trở lên. Chạy IntelliJ mặc định ở cổng 18080.
Có thể đổi cổng bằng biến môi trường `SERVER_PORT`.
Swagger: `http://localhost:18080/swagger-ui.html`.

Docker mặc định dùng cổng 18081:

```powershell
docker compose up --build -d
curl.exe http://localhost:18081/api/health
```

Swagger khi chạy Docker: `http://localhost:18081/swagger-ui.html`.

## Kiến trúc

Kiến trúc mục tiêu phân tách API, nghiệp vụ và truy cập dữ liệu.
Chi tiết trong [ARCHITECTURE.md](docs/ARCHITECTURE.md).
