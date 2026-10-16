# Single Store Service

Backend quản lý bán hàng cho một cửa hàng, sử dụng Java và Spring Boot.
Phiên bản hiện tại có model/port, JPA adapter cho user/product/order,
JWT infrastructure và nền tảng transaction/biến động kho. API nghiệp vụ
đầy đủ đang được tích hợp; `/api/health` vẫn công khai.

## Công nghệ và chức năng hiện tại

- Java 21, Spring Boot 3.5.0, REST JSON và validation dependency.
- `GET /api/health`, Swagger, test khởi động/health và unit test AuthService.
- H2 mặc định: Hibernate tạo bốn bảng JPA trước, `schema-h2.sql` tạo
  `cart_items` và `inventory_movements` sau. Flyway tắt, seed demo bật ở H2.
- `SpringTransactionRunner` và `JdbcInventoryStore`, cùng integration test
  commit/rollback JPA + JDBC, audit và ràng buộc dữ liệu.
- Maven Wrapper, Dockerfile, Compose API + MySQL và CI build/test.
- Tài liệu kiến trúc phân tầng.

Auth API, ProductController B4, API tạo/xem/tìm/hủy đơn C4/C5 đã tích hợp.
Gói D5 bổ sung API giỏ và checkout, dùng lại transaction tạo đơn và audit.
Hướng dẫn và JSON demo: [CART_API.md](docs/CART_API.md).
Chi tiết: [CART_AND_ORDER_SEARCH.md](docs/CART_AND_ORDER_SEARCH.md).
Profile MySQL đã chuẩn bị cho `single_store_team` tại cổng `3309`, dùng
Flyway và Hibernate `validate`; Compose dùng MySQL, không dùng H2.
Hướng dẫn kiểm tra nền tảng dữ liệu: [DATABASE_FOUNDATION.md](docs/DATABASE_FOUNDATION.md).

## Chạy

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Yêu cầu JDK 21 trở lên. Chạy IntelliJ mặc định ở cổng 18080.
Có thể đổi cổng bằng biến môi trường `SERVER_PORT`.
Swagger: `http://localhost:18080/swagger-ui.html`.

Docker mặc định dùng API 18081, MySQL 3309. Chuẩn bị .env riêng trước khi chạy;
thông tin đầy đủ và kiểm chứng tại [DOCKER_MYSQL.md](docs/DOCKER_MYSQL.md).

```powershell
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
curl.exe http://localhost:18081/api/health
python scripts/verify_mysql.py
```

Swagger khi chạy Docker: `http://localhost:18081/swagger-ui.html`.

## Kiến trúc

Kiến trúc mục tiêu phân tách API, nghiệp vụ và truy cập dữ liệu.
Chi tiết trong [ARCHITECTURE.md](docs/ARCHITECTURE.md).
