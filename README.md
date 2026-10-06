# Single Store Service

Backend quản lý bán hàng cho một cửa hàng, sử dụng Java và Spring Boot.
Phiên bản hiện tại cung cấp cấu trúc ứng dụng, API kiểm tra hoạt động,
tài liệu Swagger và cấu hình Docker.

## Công nghệ và chức năng hiện tại

- Java 21, Spring Boot 3.5.0, REST JSON và validation dependency.
- `GET /api/health`, Swagger, một integration test khởi động/health.
- Maven Wrapper, Dockerfile, Compose chỉ API và CI build/test.
- Tài liệu kiến trúc phân tầng.

Các module tài khoản, sản phẩm, đơn hàng và MySQL nằm trong phạm vi phát triển
tiếp theo. API `/api/health` hiện kiểm tra hoạt động của ứng dụng.

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
