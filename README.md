# Single Store Service

Backend một cửa hàng: công bố sản phẩm, đặt đơn và quản lý tồn kho. Dùng Java 21,
Spring Boot, REST/JSON và JWT; chưa có thanh toán onl, quản lý nhiều cửa hàng.

## Phạm vi và kiến trúc

| Tác nhân | Chức năng |
| --- | --- |
| Public | Xem/tìm sản phẩm ACTIVE, đăng ký và đăng nhập |
| CUSTOMER | Xem tài khoản, đổi mật khẩu, giỏ hàng, đặt/xem/tìm đơn của mình, hủy PENDING |
| ADMIN | Đổi mật khẩu, quản lý sản phẩm/kho, xem đơn toàn hệ thống và đổi trạng thái |

Luồng: HTTP + JWT filter → Controller → Service → Repository/port → Adapter JPA/JDBC → database.
Nghiệp vụ/domain không import web/DB. Đơn, trừ/hoàn kho và audit dùng cùng transaction;
checkout thành công mới xóa giỏ. Giá lấy từ database và lưu snapshot tên/giá trong đơn.
Xóa mềm sản phẩm `INACTIVE`.
Đơn đi theo `PENDING → CONFIRMED → SHIPPING → COMPLETED`; hủy `CANCELLED`.

## Cài đặt và chạy

PowerShell tại thư mục có `pom.xml` và `compose.yaml`.
Cần Git; JDK 21 nếu chạy Maven; Docker Desktop Linux Engine nếu chạy MySQL.
Python khi cần chạy các kiểm thử ngoài.

```powershell
git clone https://github.com/khoizz247/store_ktpm.git
cd store_ktpm
```

### Cách 1 H2 local

```powershell
.\mvnw.cmd -version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Chờ log `Started`; mở [Swagger H2](http://localhost:18080/swagger-ui.html).
ADMIN local: `admin@example.com` / `Admin@123`. IntelliJ: chọn SDK/Maven Runner
JDK 21, Run `SalesServiceApplication`, không bật profile `mysql`.
H2 RAM mất dữ liệu khi JVM dừng. Linux dùng `bash mvnw`.

### Cách 2 Docker MySQL

Mở Docker Desktop, chờ Engine running. Tạo `.env` nếu chưa có:

```powershell
docker info --format '{{.ServerVersion}} {{.OSType}}'
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
notepad .env
```

Giữ `MYSQL_USER=single_store_team_user`; tự điền `MYSQL_PASSWORD`,
`MYSQL_ROOT_PASSWORD`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`.
ADMIN cần mật khẩu ít nhất 12 ký tự, đủ hoa/thường/số/ký tự đặc biệt, tối đa 72 byte UTF-8.
Tạo `JWT_SECRET` rồi copy kết quả Base64 vào `.env`:

```powershell
$jwtBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
```

Mặc định API `18081`, MySQL host `3309`; đổi `API_HOST_PORT`/`MYSQL_HOST_PORT`
nếu cổng bận rồi thay URL tương ứng. Mở [Swagger Docker](http://localhost:18081/swagger-ui.html).
ADMIN dùng tài khoản bootstrap, không dùng ADMIN seed H2. Bootstrap chỉ tạo ADMIN
khi `users` còn trống, nên đặt trước khi đăng ký CUSTOMER. Đổi `.env` không đổi mật khẩu
đã lưu trong database.

Workbench: Standard TCP/IP, host `127.0.0.1`, port `3309`, username/password theo
`MYSQL_USER`/`MYSQL_PASSWORD`, schema `single_store_team`. Không phải MySQL Windows
cổng `3306`; mật khẩu database cũng không phải mật khẩu đăng nhập API.

## Dùng thử tính năng và xem đặc tả API

- File chuẩn OpenAPI: [docs/openapi.json](docs/openapi.json), có request/response,
  tham số, validation, JWT, quyền và lỗi cho đủ 28 thao tác API.
- Preview không gửi request: [docs/api-preview.html](docs/api-preview.html).
  Mở file HTML rồi chọn `openapi.json.
  Hoặc mở [Swagger Editor](https://editor.swagger.io/) và import/dán JSON.
- Swagger của ứng dụng dùng để gọi API thật. JSON sinh từ app đang chạy:
  [H2](http://localhost:18080/v3/api-docs) / [Docker](http://localhost:18081/v3/api-docs).
  Đối chiếu lại khi thay API.
- Trong Swagger: `Try it out → Execute`. Login qua `POST /api/auth/login`, copy
  riêng `accessToken` vào `Authorize` (không thêm chữ Bearer); thay token khi đổi role.

Luồng demo trên database thử, dùng SKU/email mới và ID thực trả về:

1. ADMIN tạo sản phẩm bằng `POST /api/products` với
   `{"sku":"DEMO-001","name":"Demo","price":120000,"stockQuantity":10}`.
2. Public đăng ký `POST /api/auth/register` với
   `{"fullName":"Khách demo","email":"demo-001@example.test","password":"DemoCustomer@123"}`.
   Thay token trong Authorize bằng token CUSTOMER; `GET /api/users/me` để xác nhận.
3. CUSTOMER `POST /api/orders` với body sau; thay `productId=1` bằng ID thực:
   `{"recipientName":"Khách demo","recipientPhone":"0901234567","shippingAddress":"123 Demo","items":[{"productId":1,"quantity":2}]}`.
4. Mong đợi `201/PENDING`, tiền `240000`; `GET /api/products/{id}` thấy kho `10 → 8`.
   `PATCH /api/orders/{id}/cancel` không body trả `200/CANCELLED`, kho về `10`.
5. Thử giỏ: `PUT /api/cart/items/{productId}` với `{"quantity":2}`, rồi
   `POST /api/cart/checkout` với ba trường người nhận/điện thoại/địa chỉ như trên,
   không truyền `items`; mong đợi kho giảm và `GET /api/cart` trả giỏ rỗng.
   Thêm giỏ chưa giữ chỗ tồn kho; checkout không có bước thanh toán online.

Các thao tác sửa/xóa sản phẩm, điều chỉnh kho, đổi mật khẩu, tìm đơn và quản lý
trạng thái: xem nhóm tương ứng trong OpenAPI/Swagger. ADMIN không dùng API đặt đơn CUSTOMER.
Có thể chạy [demo đặt đơn](docs/order-demo.http) hoặc [demo giỏ](docs/checkout-demo.http)
bằng IntelliJ HTTP Client; dùng môi trường `h2` hoặc khai báo môi trường Docker riêng.

## Kiểm thử và xử lý lỗi

```powershell
.\mvnw.cmd clean verify
python -m unittest discover -s scripts -p "test_*.py"
python -m unittest performance.test_report
python scripts/verify_mysql.py --base-url http://localhost:18081
```

JUnit mặc định dùng H2; báo cáo ở `target/surefire-reports` và `target/failsafe-reports`.
Script Python cuối kiểm tra Docker MySQL đang chạy; không thay JUnit.
Có thể thêm `--exercise-orders`, `--exercise-cart` hoặc `--exercise-cart-concurrently`
để thử luồng thật: **tạo dữ liệu, trừ kho và giữ đơn/audit**, chỉ dùng DB demo.
Kaggle CPU: import `performance/kaggle_load_test.ipynb`, bật Internet, nhập SHA đã push,
chạy đủ cell và tải ZIP kết quả; [hướng dẫn chạy](performance/README.md).

Đọc log: `docker compose logs --tail=150 api mysql`. `401`: thiếu/sai token;
`403`: sai role; `404`: sai ID/không sở hữu/INACTIVE; `409`: đọc message để kiểm tra
trùng dữ liệu, thiếu kho hoặc trạng thái. `/api/health` chỉ xác nhận tiến trình,
không xác nhận database. `docker compose stop` giữ dữ liệu; `up -d` chạy lại;
sửa Java dùng `up --build`. Không dùng `down -v` để chữa lỗi vì sẽ mất volume MySQL.
