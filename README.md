# Single Store Service

Hướng dẫn cài đặt, chạy ứng dụng, sử dụng tính năng và kiểm thử backend một cửa hàng.
Đặc tả nghiệp vụ, kiến trúc, tiến độ và báo cáo được trình bày trong tài liệu ngoài.

## 1. Chuẩn bị

- Git để lấy mã nguồn.
- JDK 21 để chạy trên máy; không cần cài Maven riêng vì có Maven Wrapper.
- Docker Desktop với Linux Engine và Docker Compose để chạy bản MySQL.
- Python 3 nếu chạy các script kiểm tra MySQL hoặc kiểm thử Python.

Lệnh bên dưới dùng PowerShell, chạy trong thư mục có `pom.xml` và `compose.yaml`.
Chỉ copy dòng lệnh, không copy dấu ba backtick bao quanh khối code.

```powershell
git clone https://github.com/khoizz247/store_ktpm.git
cd store_ktpm
```

Chọn một trong hai cách chạy:

| Cách chạy | Swagger mặc định | Database | ADMIN |
| --- | --- | --- | --- |
| Maven hoặc IntelliJ | http://localhost:18080/swagger-ui.html | H2 trong RAM | `admin@example.com` / `Admin@123`, chỉ local |
| Docker Compose | http://localhost:18081/swagger-ui.html | MySQL, cổng host `3309` | Tự cấu hình trước lần chạy đầu |

Hai môi trường không dùng chung tài khoản/dữ liệu. H2 mất dữ liệu khi dừng JVM;
MySQL Docker giữ dữ liệu trong named volume.

## 2. Chạy nhanh bằng H2

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Chờ log `Started SalesServiceApplication`, rồi mở Swagger cổng `18080`.
Dừng bằng `Ctrl+C` tại terminal đang chạy ứng dụng.

IntelliJ: mở dự án Maven, đặt Project SDK và Maven Runner là JDK 21 rồi Run
`SalesServiceApplication`. Không bật profile `mysql` nếu muốn dùng H2.
File `.env` của Docker không được Maven/IntelliJ tự động đọc.
Nếu cổng bận, đặt `SERVER_PORT` thành cổng trống trong Run Configuration hoặc
terminal chạy app, rồi mở Swagger bằng cổng đã chọn.

## 3. Chạy bằng Docker và MySQL

### Cấu hình lần đầu

Mở Docker Desktop, chờ Engine running rồi kiểm tra:

```powershell
docker info --format '{{.ServerVersion}} {{.OSType}}'
docker compose version
```

Cần có Docker Server và OSType là `linux`. Tạo `.env` nếu chưa có, không ghi đè file đang dùng:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
notepad .env
```

Điền các biến, mỗi biến một dòng:

| Biến | Giá trị cần đặt |
| --- | --- |
| `MYSQL_USER` | Giữ `single_store_team_user` hoặc chọn user riêng, không dùng `root` |
| `MYSQL_PASSWORD` | Mật khẩu riêng cho user database ứng dụng |
| `MYSQL_ROOT_PASSWORD` | Mật khẩu quản trị MySQL Docker, nên khác mật khẩu ứng dụng |
| `MYSQL_HOST_PORT` | Mặc định `3309` |
| `API_HOST_PORT` | Mặc định `18081`; nếu dùng `18082`, đổi các URL bên dưới theo cổng này |
| `JWT_SECRET` | Base64 của ít nhất 32 byte ngẫu nhiên |
| `BOOTSTRAP_ADMIN_EMAIL` | Email ADMIN đăng nhập API |
| `BOOTSTRAP_ADMIN_PASSWORD` | Ít nhất 12 ký tự, có hoa/thường/số/ký tự đặc biệt, tối đa 72 byte UTF-8 |

Tạo JWT secret rồi copy kết quả vào `JWT_SECRET` trong `.env`:

```powershell
$jwtBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
```

Đặt cả hai biến bootstrap trước lần chạy đầu để có ADMIN dùng thử.
Bootstrap chỉ tạo ADMIN khi bảng `users` còn trống. Đăng ký công khai chỉ tạo CUSTOMER.
Email/mật khẩu ADMIN API khác username/mật khẩu MySQL.

Không commit `.env`, JWT hay mật khẩu thật. Đổi `.env` không tự đổi mật khẩu
MySQL trong volume cũ hoặc mật khẩu ADMIN đã tồn tại.

### Build và khởi động

```powershell
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
curl.exe http://localhost:18081/api/health
python scripts/verify_mysql.py --base-url http://localhost:18081
```

Build đầu có thể mất vài phút để tải dependency. Chờ API và MySQL cùng `healthy`.
Script kiểm tra cần chạy trong đúng project Compose; nó xác nhận schema, migration,
API health và OpenAPI. Riêng `/api/health` không kiểm tra kết nối database.

Mở http://localhost:18081/swagger-ui.html. Nếu đổi `API_HOST_PORT`, thay cổng
trong URL và `--base-url`; không đổi cổng nội bộ `8080` của container.

### Dừng chạy lại và xem log

```powershell
docker compose ps
docker compose logs --tail=150 api
docker compose logs --tail=150 mysql
docker compose stop
docker compose up -d --wait --wait-timeout 240
```

- `stop` dừng container, giữ dữ liệu.
- `up -d` chạy lại và áp dụng cấu hình Compose; sau khi sửa Java, dùng lại `up --build` ở trên.
- `restart api` không build mã mới hay áp dụng `.env` mới.
- `docker compose down` gỡ container/network, giữ named volume.
- Không dùng `docker compose down -v` để chữa lỗi: lệnh này xóa volume và dữ liệu MySQL.

### Kết nối MySQL Workbench

Tạo connection Standard TCP/IP: hostname `127.0.0.1`, port theo `MYSQL_HOST_PORT`
(mặc định `3309`), username theo `MYSQL_USER`, password theo `MYSQL_PASSWORD`.
Test Connection rồi mở schema `single_store_team`.
Không dùng connection MySQL Windows cổng `3306` để tìm dữ liệu Docker.

```sql
USE single_store_team;
SHOW TABLES;
SELECT id, sku, name, stock_quantity, status FROM products;
SELECT id, order_code, user_id, status, total_amount FROM orders;
SELECT product_id, movement_type, quantity_change, stock_before, stock_after
FROM inventory_movements ORDER BY id DESC;
```

## 4. Sử dụng tính năng trên Swagger

Mỗi API: chọn method/đường dẫn → `Try it out` → nhập tham số/body →
`Execute` → đọc HTTP status và Response body. Chỉ thử thao tác ghi trên database demo.
Thay `{id}` và `{productId}` bằng ID thực, không nhập nguyên dấu ngoặc nhọn.

### Đăng nhập ADMIN và tạo sản phẩm

1. Gọi `POST /api/auth/login` bằng ADMIN của môi trường đang chạy:
   Docker dùng tài khoản bootstrap trong `.env`; H2 dùng tài khoản local ở mục 1.
2. Body: `{"email":"EMAIL_ADMIN","password":"MAT_KHAU_ADMIN"}`;
   thay hai giá trị trước khi gửi. Mong đợi `200`, `role=ADMIN`.
3. Copy riêng `accessToken`, bấm `Authorize`, dán token rồi `Authorize` → `Close`.
   Không thêm chữ `Bearer` vào ô này vì Swagger tự thêm.
4. Gọi `POST /api/products` với body sau; mong đợi `201`. Ghi lại `id` sản phẩm.

```json
{
  "sku": "DEMO-README-001",
  "name": "Sản phẩm thử nghiệm",
  "description": "Chỉ dùng trên database demo",
  "price": 120000,
  "stockQuantity": 10
}
```

Đổi SKU nếu đã tồn tại (`409`).

### Đăng ký CUSTOMER đặt đơn và hủy đơn

1. Gọi `POST /api/auth/register` với body sau; đổi email nếu đã đăng ký.
   Mong đợi `201`, `role=CUSTOMER`.

```json
{
  "fullName": "Khách hàng demo",
  "email": "readme-customer-001@example.test",
  "password": "DemoCustomer@123"
}
```

2. Trong `Authorize`, Logout token ADMIN và thay bằng `accessToken` CUSTOMER.
   Có thể gọi `POST /api/auth/login` để lấy lại token.
3. Gọi `GET /api/users/me` để xác nhận đúng tài khoản.
4. Gọi `POST /api/orders` với body sau. **Thay `productId: 1` bằng ID sản phẩm đã tạo.**

```json
{
  "recipientName": "Khách hàng demo",
  "recipientPhone": "0901234567",
  "shippingAddress": "123 Đường Demo, Hà Nội",
  "items": [{"productId": 1, "quantity": 2}]
}
```

5. Mong đợi `201`, `PENDING`, tổng tiền `240000`; ghi lại `id` đơn.
6. Gọi `GET /api/products/{id}`: kho từ `10` còn `8` nếu không có giao dịch khác.
7. Gọi `PATCH /api/orders/{id}/cancel` bằng chính CUSTOMER đó, không cần body:
   mong đợi `200`, `CANCELLED`; đọc lại sản phẩm thấy kho về `10`.
   Hủy lại đơn này trả `409`, không hoàn kho thêm.

### Giỏ hàng và checkout

Dùng token CUSTOMER, sản phẩm ACTIVE và đủ kho:

1. `PUT /api/cart/items/{productId}` với `{"quantity":2}`: đặt số lượng là 2, không phải cộng thêm 2.
2. `GET /api/cart` để xem giỏ. Thêm giỏ chưa trừ hoặc giữ chỗ tồn kho.
3. `POST /api/cart/checkout` với body sau; không truyền `items`.

```json
{
  "recipientName": "Khách hàng demo",
  "recipientPhone": "0901234567",
  "shippingAddress": "123 Đường Demo, Hà Nội"
}
```

4. Mong đợi `201`, đơn `PENDING`, kho giảm 2; `GET /api/cart` trả giỏ rỗng.
5. Xóa một món bằng `DELETE /api/cart/items/{productId}` hoặc xóa cả giỏ bằng `DELETE /api/cart` (`204`).

### Thử các tính năng còn lại

| Thao tác | Cách dùng trên Swagger |
| --- | --- |
| Xem/tìm sản phẩm | `GET /api/products` hoặc `/api/products/search`; nhập `q`, `page=0`, `size=20`; không cần token |
| Xem/tìm đơn của mình | CUSTOMER gọi `GET /api/orders/me`, `/api/orders/{id}` hoặc `/api/orders/me/search` với `code`, `status` |
| Sửa sản phẩm | ADMIN gọi `PUT /api/products/{id}`; điền `name`, `description`, `price`, `stockQuantity` |
| Đặt lại số tồn | ADMIN gọi `PATCH /api/products/{id}/stock` với `{"stockQuantity":15}`; là số tồn mới, không phải cộng 15 |
| Tăng/giảm kho | ADMIN gọi `POST /api/admin/products/{productId}/inventory/adjustments` với `{"quantityChange":5,"note":"Nhập hàng demo"}`; dùng số âm nếu giảm |
| Xem lịch sử kho | ADMIN gọi `GET /api/admin/products/{productId}/inventory` |
| Xem/tìm đơn toàn hệ thống | ADMIN gọi `GET /api/admin/orders`, `/api/admin/orders/search` hoặc `/api/admin/orders/{id}` |
| Chuyển trạng thái đơn | ADMIN gọi `PATCH /api/admin/orders/{id}/status` với `{"status":"CONFIRMED"}`; dùng đơn mới chưa hủy, theo thứ tự `PENDING → CONFIRMED → SHIPPING → COMPLETED` |
| Hủy đơn phía ADMIN | API cập nhật trạng thái với `{"status":"CANCELLED"}` khi đơn `PENDING` hoặc `CONFIRMED` |
| Đổi mật khẩu | `POST /api/users/me/password` với `currentPassword`, `newPassword`; mật khẩu mới đủ mạnh như yêu cầu ADMIN ở mục 3 và khác mật khẩu cũ; thành công `204`, thử login lại |
| Xóa mềm sản phẩm | ADMIN gọi `DELETE /api/products/{id}` (`204`); sản phẩm chuyển `INACTIVE`, GET công khai theo ID trả `404`. Thử bước này cuối cùng |

IntelliJ HTTP Client có sẵn [luồng đặt đơn](docs/order-demo.http) và
[luồng giỏ hàng](docs/checkout-demo.http). Chọn môi trường `h2`;
với Docker, đặt `base`, `adminEmail`, `adminPassword` theo môi trường riêng trong
`docs/http-client.private.env.json`, không commit file này.

## 5. Chạy kiểm thử

### Java và Python

```powershell
.\mvnw.cmd clean verify
python -m unittest discover -s scripts -p "test_*.py"
python -m unittest performance.test_report
```

Maven `verify` chạy unit/integration test theo cấu hình dự án. Mong đợi
`BUILD SUCCESS`, không failure/error. Báo cáo: `target/surefire-reports` và
`target/failsafe-reports`. Maven mặc định dùng H2, không yêu cầu Docker MySQL.
Python unit test không chạy tải thật và không thay thế kiểm tra MySQL bên dưới.

### Smoke test với Docker MySQL

Chạy sau khi Compose healthy. Các tùy chọn `--exercise-*` **tạo tài khoản/đơn/audit,
trừ kho và giữ dữ liệu**, không tự hủy đơn. Chỉ dùng database demo.

```powershell
python scripts/verify_mysql.py --base-url http://localhost:18081
python scripts/verify_mysql.py --base-url http://localhost:18081 --exercise-orders
python scripts/verify_mysql.py --base-url http://localhost:18081 --exercise-cart
python scripts/verify_mysql.py --base-url http://localhost:18081 --exercise-cart-concurrently
```

Đổi cổng theo `API_HOST_PORT`. Kiểm tra checkout cạnh tranh không thay thế kiểm thử tải.

### Chạy tải trên Kaggle CPU

1. Commit/push phiên bản muốn đo, ghi SHA bằng `git rev-parse HEAD`.
2. Tạo Kaggle Python Notebook, chọn CPU (`Accelerator=None`), bật Internet.
3. Import riêng `performance/kaggle_load_test.ipynb`, không upload cả thư mục.
4. Chạy cell clone/checkout, nhập SHA đã push. Chạy cell runner; không đặt
   `LOAD_SMOKE_ONLY=1` nếu muốn đủ bốn pha.
5. Chờ các pha 5/10/30/60 người dùng hoàn tất, chạy cell đóng gói kết quả.
6. Tải `/kaggle/working/single_store_load_results.zip` trong Output trước khi kết thúc phiên.

MySQL/API chạy trong Kaggle, không dùng Docker/database trên máy cá nhân.
Mỗi lần đo độc lập dùng session sạch; nếu runner lỗi, đọc log trước, không đổi sang H2.
Chi tiết thao tác notebook: [performance/README.md](performance/README.md).

## 6. Khi không chạy được

| Hiện tượng | Kiểm tra/thao tác |
| --- | --- |
| Maven sai Java | Xem `.\mvnw.cmd -version`, chỉnh JAVA_HOME hoặc Maven Runner về JDK 21 |
| Docker không có Server hoặc không kết nối Engine | Mở Docker Desktop, chờ Linux Engine running rồi thử `docker info` |
| API vừa chạy chưa phản hồi | Chờ healthcheck, đọc `docker compose logs --tail=150 api` |
| Cổng bị chiếm | Đổi cổng host trong `.env`, chạy lại Compose, sửa URL/Workbench theo cổng mới |
| MySQL Access denied sau khi đổi `.env` | Kiểm tra mật khẩu lúc khởi tạo volume; đổi `.env` không đổi tài khoản database cũ. Không xóa volume để thử |
| ADMIN Docker không login được | Kiểm tra bootstrap đã tạo ADMIN khi `users` còn trống; không dùng ADMIN seed H2 |
| `400` | Kiểm tra JSON/trường bắt buộc/số lượng; `page >= 0`, `size` từ 1 đến 100 |
| `401` hoặc `403` | Login lại và thay token; CUSTOMER dùng giỏ/đặt đơn, ADMIN dùng quản trị |
| `404` | Kiểm tra ID, sản phẩm ACTIVE và quyền sở hữu đơn |
| `409` | Đọc message: có thể trùng email/SKU, thiếu kho, giỏ trống hoặc trạng thái đơn không cho phép |

Đọc log tại máy để debug; không chia sẻ log có secret, token hoặc dữ liệu cá nhân.
