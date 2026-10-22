# Single Store Service

Backend quản lý bán hàng cho một cửa hàng, sử dụng Java và Spring Boot.
Phạm vi: một cửa hàng, một tồn kho cho mỗi sản phẩm, hai vai trò CUSTOMER/ADMIN.
Không quản lý chi nhánh, thanh toán online hay vận chuyển bên thứ ba.

## Công nghệ và chức năng hiện tại

- Java 21, Spring Boot 3.5.0, REST JSON và validation dependency.
- Đăng ký CUSTOMER, đăng nhập JWT, xem tài khoản và đổi mật khẩu.
- CRUD/soft delete sản phẩm, phân trang/tìm tên hoặc SKU và audit tồn kho.
- Giỏ hàng, checkout, tạo/xem/tìm đơn, khách hủy đơn PENDING, admin quản lý trạng thái.
- Tổng tiền lấy từ database; snapshot tên/giá, trừ/hoàn kho trong cùng transaction.
- H2 mặc định: Hibernate tạo bốn bảng JPA trước, `schema-h2.sql` tạo
  `cart_items` và `inventory_movements` sau. Flyway tắt, seed demo bật ở H2.
- `SpringTransactionRunner` và `JdbcInventoryStore`, cùng integration test
  commit/rollback JPA + JDBC, audit và ràng buộc dữ liệu.
- Maven Wrapper, Dockerfile, Compose API + MySQL và CI build/test.
- Tài liệu kiến trúc phân tầng và đặc tả API/quyền truy cập.

Danh sách API/quyền, quy tắc và response: [API_SPEC.md](docs/API_SPEC.md).
Request mẫu cho IntelliJ HTTP Client: [checkout-demo.http](docs/checkout-demo.http).
Hướng dẫn giỏ và JSON demo: [CART_API.md](docs/CART_API.md).
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

H2 là database RAM phục vụ học/demo, không phải MySQL. Tài khoản seed H2:
`admin@example.com` / `Admin@123` (chỉ local). MySQL không tạo tài khoản này;
admin MySQL dùng BOOTSTRAP_ADMIN_EMAIL/PASSWORD riêng trước lần khởi động đầu.
Không commit `.env` hoặc mật khẩu thật. Xem hướng dẫn Docker bên dưới.

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

API → application service → repository/port → JPA/JDBC → database.
Business/domain không import Spring, web hoặc DB; config nối bean.
Chi tiết trong [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Kiểm thử và CI

`mvnw verify` chạy Test bằng Surefire và IT bằng Failsafe, gồm kiểm tra kiến trúc
ArchUnit. Workflow có job Java/Python test và job Compose MySQL riêng theo mỗi
lượt chạy. Không dùng .env hoặc volume demo của nhóm trên CI.
Hướng dẫn, phạm vi rule và cách nghiệm thu: [CI_ARCHITECTURE.md](docs/CI_ARCHITECTURE.md).

Maven mặc định dùng H2 riêng cho test, không cần MySQL trên cổng 3306 và không
đọc mật khẩu database trong `.env`. MySQL thật được kiểm tra bằng job Compose
riêng và script readiness/smoke. Test H2 không chứng minh migration MySQL đúng.

## Nghiệm thu Pha 1

Tình trạng và các sửa lỗi: [FINAL_REVIEW.md](docs/FINAL_REVIEW.md).
Không dùng kết quả Kaggle của repository tham chiếu để kết luận hiệu năng bản nhóm.
Runner, notebook và cách thu CSV: [performance/README.md](performance/README.md).
Baseline `941ec24` đã có CI xanh và kết quả Kaggle CPU đủ bốn pha, đối chiếu
CSV/HTML/log: [báo cáo tải](performance/REPORT.md). Đủ minh chứng nghiệm thu
Pha 1 theo đề bài đã cung cấp; không đồng nghĩa sẵn sàng production hoặc đã tối ưu.
