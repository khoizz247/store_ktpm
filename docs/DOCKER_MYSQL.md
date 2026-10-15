# Chạy API và MySQL của nhóm

## Phạm vi D4

Compose dùng project single-store-team, database single_store_team, MySQL host
3309 và API host 18081. Không dùng database/volume single_store của bản tham chiếu.
MySQL tạo database rỗng; Flyway trong API chạy V1/V2, sau đó Hibernate validate.
Không mount SQL vào docker-entrypoint-initdb.d và không dùng ddl-auto update cho MySQL.

## Chuẩn bị cấu hình riêng

Nếu chưa có .env, copy .env.example sang .env, điền MYSQL_PASSWORD,
MYSQL_ROOT_PASSWORD và JWT_SECRET. JWT_SECRET là Base64 của ít nhất 32 byte ngẫu nhiên.
Không dùng một mật khẩu đọc được thay cho Base64. Có thể tạo secret bằng PowerShell:

```powershell
$jwtBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
```

Chỉ copy kết quả vào .env cục bộ, không gửi lên chat/PR hoặc commit. File .env đã
được .gitignore và .dockerignore loại trừ. .env.example không chứa secret thật.

BOOTSTRAP_ADMIN_EMAIL/PASSWORD là tùy chọn; đặt cả hai hoặc bỏ trống cả hai.
Password admin cần ít nhất 12 ký tự, đủ chữ hoa/thường/số/ký tự đặc biệt và
không vượt 72 byte UTF-8. Bootstrap chỉ tạo admin khi users chưa có tài khoản.
Nếu muốn admin đầu tiên, thiết lập trước khi đăng ký customer. Đổi .env không
tự thay mật khẩu của tài khoản đã có hoặc mật khẩu MySQL trong volume cũ.

## Khởi động

Kiểm tra Docker Desktop đã chạy Linux engine. Dừng phiên IntelliJ khác đang dùng
18081; không dừng/xóa container dự án khác. Nếu base Docker cũ chiếm cổng, dùng
Docker Desktop để xác định đúng single-store-team-base rồi dừng riêng nó,
hoặc đổi API_HOST_PORT trong .env. Không dùng down -v để chữa lỗi.

```powershell
docker info --format '{{.ServerVersion}} {{.OSType}}'
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
curl.exe http://localhost:18081/api/health
```

Không in docker compose config đầy đủ vì chứa secret sau nội suy. Cổng chỉ bind
127.0.0.1 để phục vụ demo local. Swagger: http://localhost:18081/swagger-ui.html.
API trong Docker kết nối mysql:3306, không dùng localhost:3309 bên trong container.

## Kiểm chứng không sửa dữ liệu

```powershell
python scripts/verify_mysql.py
```

Script kiểm tra database đúng tên, đủ sáu bảng nghiệp vụ, flyway_schema_history,
V1/V2 success, health và OpenAPI. Nếu đổi API_HOST_PORT, truyền --base-url.

## Smoke API đơn hàng có ghi dữ liệu

Chạy khi database demo không có người mua đồng thời. Lệnh dưới tạo hai customer,
một đơn PENDING, trừ một sản phẩm và giữ dữ liệu/audit để đối chiếu. Không tự hủy
vì API hủy thuộc bước sau. Không chạy trên database chứa dữ liệu quan trọng.

```powershell
python scripts/verify_mysql.py --exercise-orders
```

Script dùng API register/login/order/list/detail hiện có của C4, kiểm tra 401 và
khách khác không xem được đơn. Chưa yêu cầu ProductController vì B4 chưa tích hợp.
Sản phẩm mẫu từ migration V2 cung cấp dữ liệu để thử đơn.

## MySQL Workbench

Tạo connection Standard TCP/IP: 127.0.0.1, port 3309, username bằng MYSQL_USER và
password bằng MYSQL_PASSWORD trong .env. Đây không phải email/password admin API.

```sql
USE single_store_team;
SHOW TABLES;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, email, role FROM users;
SELECT id, sku, stock_quantity FROM products;
SELECT id, order_code, status, total_amount FROM orders;
```

## Kiểm thử và giới hạn bằng chứng

```powershell
.\mvnw.cmd verify
python -m unittest discover -s scripts -p test_verify_mysql.py
```

Python unit test chỉ kiểm tra script bằng mock, KHÔNG thay cho MySQL thật.
Maven mặc định dùng H2; không suy ra migration MySQL đã chạy từ test H2 xanh.
API health chỉ kiểm tra tiến trình; phải chạy script để kiểm chứng schema/migration.
Đọc logs API/MySQL tại máy nếu lỗi; không đăng log chứa secret lên PR.
Compose không tạo view/trigger như dự án sales-service cũ; schema nhóm có sáu bảng
nghiệp vụ cộng bảng lịch sử Flyway. Kiểm thử load/concurrency thuộc các mốc sau.

## Kết quả nghiệm thu local ngày 16 tháng 10 năm 2026

Theo log chạy trên máy người dùng: Maven verify hoàn tất 29 test, không lỗi hoặc
bỏ qua, BUILD SUCCESS. API và MySQL đều healthy; script xác nhận đủ sáu bảng,
Flyway V1/V2 success, health và OpenAPI. Smoke register/login/create/list/detail,
401 và owner isolation trên MySQL thành công.

Máy demo dùng API_HOST_PORT=18082 trong .env vì 18081 bị chiếm. Đây chỉ là
override local, không cần đổi cổng mặc định 18081 của Compose. Khi dùng cổng này:

```powershell
python scripts/verify_mysql.py --base-url http://localhost:18082
python scripts/verify_mysql.py --base-url http://localhost:18082 --exercise-orders
```

Hai lần chạy order smoke đã tạo hai đơn riêng và bốn customer demo; kho giảm
20 → 19 → 18 là đúng. Không suy ra hủy đơn/checkout/concurrency đã được nghiệm thu.
